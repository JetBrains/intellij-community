/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author ven
 */
public class GroovyCodeFragmentFactory implements CodeFragmentFactory {
  private static final String EVAL_NAME = "_JETGROOVY_EVAL_";
  private static final String IMPORTS = "___$$IMPORTS$$___";
  private static final String TEXT = "___$$TEXT$$___";

  private static String unwrapVals(List<String> vals) {
    return "java.lang.Object[] |vals = new java.lang.Object[]{" + StringUtil.join(vals, ",") + "};\n" +
           "java.lang.Object[] |resVals = new java.lang.Object[" + vals.size() + "];\n" +
           "for (int |iii =0; |iii<|vals.length; |iii++){java.lang.Object |o = |vals[|iii];\n" +
           "if (|o instanceof groovy.lang.Reference) {|o = ((groovy.lang.Reference)|o).get();}\n" +
           "|resVals[|iii] = |o;" +
           "}\n";
  }

  public JavaCodeFragment createCodeFragment(TextWithImports textWithImports, PsiElement context, Project project) {
    String text = textWithImports.getText();
    String imports = textWithImports.getImports();

    final Pair<Map<String, String>, GroovyFile> pair = externalParameters(text, context);
    GroovyFile toEval = pair.second;
    final Map<String, String> parameters = pair.first;

    List<String> names = new ArrayList<String>(parameters.keySet());
    List<String> values = ContainerUtil.map(names, new Function<String, String>() {
      public String fun(String name) {
        return parameters.get(name);
      }
    });


    text = toEval.getText();
    final String groovyText = StringUtil.join(names, ", ") + "->" + stripImports(text, toEval);

    PsiClass contextClass = PsiUtil.getContextClass(context);
    boolean isStatic = isStaticContext(context);
    StringBuffer javaText = new StringBuffer();

    javaText.append("groovy.lang.MetaClass |mc;\n");
    javaText.append("java.lang.Class |clazz;\n");
    if (!isStatic) {
      javaText.append("|clazz = ((java.lang.Object)this).getClass();\n");
      javaText.append("|mc = ((groovy.lang.GroovyObject)this).getMetaClass();\n");
    } else {
      assert contextClass != null;
      javaText.append("|clazz = java.lang.Class.forName(\"").append(contextClass.getQualifiedName()).append("\");\n");
      javaText.append("|mc = groovy.lang.GroovySystem.getMetaClassRegistry().getMetaClass(|clazz);\n");
    }

    javaText.append("final java.lang.ClassLoader |parentLoader = |clazz.getClassLoader();\n" +
                    "   final groovy.lang.GroovyClassLoader |loader = new groovy.lang.GroovyClassLoader(|parentLoader);\n" +
                    "   final java.lang.Class |c = |loader.parseClass(");
    javaText.append("\"" + IMPORTS + "class DUMMY { " +
                       "public groovy.lang.Closure " +
                       EVAL_NAME + " = {" + TEXT + "}}\"");
    javaText.append(", \"DUMMY.groovy\");\n" +
                    "   int |i;\n" +
                    "   java.lang.reflect.Field[] |fields = |c.getFields();\n" +
                    "   for (int |j = 0; |j < |fields.length; |j++) if (|fields[|j].getName().equals(\"_JETGROOVY_EVAL_\")) {|i = |j; break;}\n" +
                    "   final java.lang.reflect.Field |field = |fields[|i];\n" +
                    "   final java.lang.Object |closure = |field.get(|c.newInstance());\n");

    javaText.append("groovy.lang.ExpandoMetaClass |emc = new groovy.lang.ExpandoMetaClass(|clazz);\n");
    if (!isStatic) {
      javaText.append("|emc.setProperty(\"").append(EVAL_NAME).append("\", |closure);\n");
      javaText.append("((groovy.lang.GroovyObject)this).setMetaClass(|emc);\n");
    } else {
      javaText.append("((groovy.lang.GroovyObject)|emc.getProperty(\"static\")).setProperty(\"").append(EVAL_NAME).append("\", |closure);\n");
      javaText.append("groovy.lang.GroovySystem.getMetaClassRegistry().setMetaClass(|clazz, |emc);\n");
    }
    javaText.append("|emc.initialize();\n");
    javaText.append(unwrapVals(values));
    if (!isStatic) {
      javaText.append("java.lang.Object |res = ((groovy.lang.MetaClassImpl)|emc).invokeMethod(this, \"").append(EVAL_NAME).append("\", |resVals);\n");
      javaText.append("((groovy.lang.GroovyObject)this).setMetaClass(|mc);"); //try/finally is not supported
    } else {
      javaText.append("java.lang.Object |res = ((groovy.lang.MetaClassImpl)|emc).invokeStaticMethod(|clazz, \"").append(EVAL_NAME).append("\", |resVals);\n");
      javaText.append("groovy.lang.GroovySystem.getMetaClassRegistry().setMetaClass(|clazz, |mc);\n");
    }
    javaText.append("|res");

    final PsiElementFactory factory = JavaPsiFacade.getInstance(toEval.getProject()).getElementFactory();

    final String hiddenJavaVars = StringUtil.replace(javaText.toString(), "|", "_$$_$$$_$$$$$$$$$_" + new Random().nextInt(42));
    final String finalText = StringUtil.replace(StringUtil.replace(hiddenJavaVars, TEXT, groovyText), IMPORTS, imports);
    JavaCodeFragment result = factory.createCodeBlockCodeFragment(finalText, null, true);
    if (contextClass != null) {
      result.setThisType(factory.createType(contextClass));
    }
    return result;
  }

  public static Pair<Map<String, String>, GroovyFile> externalParameters(String text, @NotNull final PsiElement context) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    final GroovyFile toEval = factory.createGroovyFile(text, false, context);

    final GrClosableBlock closure = PsiTreeUtil.getParentOfType(context, GrClosableBlock.class);
    final Map<String, String> parameters = new THashMap<String, String>();
    toEval.accept(new GroovyRecursiveElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        PsiElement resolved = referenceExpression.resolve();

        if (resolved instanceof PsiMethod && "getDelegate".equals(((PsiMethod) resolved).getName()) && closure != null) {
          replaceWithReference(referenceExpression, "owner");
          return;
        }

        if (resolved instanceof GrField && !referenceExpression.isQualified()) {
          replaceWithReference(referenceExpression, (closure == null ? "delegate" : "owner") + "." + referenceExpression.getReferenceName());
          return;
        }

        if (resolved instanceof GrVariableBase && !(resolved instanceof GrField) && !PsiTreeUtil.isAncestor(toEval, resolved, false)) {
          final String name = ((GrVariableBase)resolved).getName();
          if (resolved instanceof ClosureSyntheticParameter && PsiTreeUtil.isAncestor(toEval, ((ClosureSyntheticParameter) resolved).getClosure(), false)) {
            return;
          }
          String value;
          if (closure != null &&
              PsiTreeUtil.findCommonParent(resolved, closure) != closure &&
              !(resolved instanceof ClosureSyntheticParameter)) {
            // Evaluating inside closure for outer variable definitions
            // All non-local variables are accessed by references
            value = "this." + name;
          } else {
            value = name;
          }
          parameters.put(name, value);
        }

        if (resolved instanceof PsiClass) {
          String qName = ((PsiClass)resolved).getQualifiedName();
          if (qName != null && qName.contains(".") && !referenceExpression.isQualified()) {
            replaceWithReference(referenceExpression, qName);
          }
        }
      }

      @Override
      public void visitThisExpression(final GrThisReferenceExpression thisExpression) {
        super.visitThisExpression(thisExpression);
        replaceWithReference(thisExpression, closure == null ? "delegate" : "owner");
      }

      @Override
      public void visitSuperExpression(final GrSuperReferenceExpression superExpression) {
        super.visitSuperExpression(superExpression);
        replaceWithReference(superExpression, closure == null ? "delegate" : "owner");
      }

      private void replaceWithReference(GrExpression expr, final String exprText) {
        final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expr.getProject());
        visitReferenceExpression((GrReferenceExpression)expr.replaceWithExpression(factory.createExpressionFromText(exprText), false));
      }

      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        if (refElement.getQualifier() != null) {
          super.visitCodeReferenceElement(refElement);
        } else {
          PsiElement resolved = refElement.resolve();
          if (resolved instanceof PsiClass) {
            String qName = ((PsiClass)resolved).getQualifiedName();
            if (qName != null) {
              int dotIndex = qName.lastIndexOf(".");
              if (dotIndex < 0) return;
              String packageName = qName.substring(0, dotIndex);
              refElement.setQualifier(factory.createReferenceElementFromText(packageName));
            }
          }
        }
      }
    });
    return Pair.create(parameters, toEval);
  }

  private static String stripImports(String text, GroovyFile toEval) {
    GrImportStatement[] imports = toEval.getImportStatements();
    for (int i = imports.length - 1; i >= 0; i--) {
      TextRange range = imports[i].getTextRange();
      text = text.substring(0, range.getStartOffset()) + text.substring(range.getEndOffset(), text.length());
    }
    return StringUtil.escapeStringCharacters(text);
  }

  public JavaCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project) {
    GroovyCodeFragment result = new GroovyCodeFragment(project, item.getText());
    result.setContext(context);
    return result;
  }

  private static boolean isStaticContext(PsiElement context) {
    PsiElement parent = context;
    while (parent != null) {
      if (parent instanceof PsiModifierListOwner && ((PsiModifierListOwner)parent).hasModifierProperty(PsiModifier.STATIC)) return true;
      if (parent instanceof GrTypeDefinition || parent instanceof GroovyFile) return false;
      parent = parent.getParent();
    }

    return false;
  }

  public boolean isContextAccepted(PsiElement context) {
    return context != null && context.getLanguage().equals(GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  public String getDisplayName() {
    return "Groovy";
  }

  public LanguageFileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }
}
