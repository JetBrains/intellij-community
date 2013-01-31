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

import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMethodFix;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author ven
 */
public class GroovyCodeFragmentFactory extends CodeFragmentFactory {
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
    StringBuilder javaText = new StringBuilder();

    javaText.append("groovy.lang.MetaClass |mc;\n");
    javaText.append("java.lang.Class |clazz;\n");

    if (!isStatic) {
      javaText.append("java.lang.Object |thiz0;\n");

      String fileName;

      PsiElement originalContext = context.getContainingFile().getContext();
      if (originalContext == null) {
        fileName = null; // The class is not reloaded by springloaded if context is in physical file.
      }
      else {
        fileName = originalContext.getContainingFile().getOriginalFile().getName();
      }

      if (fileName == null) {
        // The class could not be reloaded by springloaded
        javaText.append("|thiz0 = this;\n");
      }
      else {
        // Class could be reloaded by springloaded

        String s = StringUtil.escapeStringCharacters(Pattern.quote(fileName));
        // We believe what class is reloaded if stacktrace matches one of two patterns:
        // 1.) [com.package.Foo$$ENLbVXwm.methodName(FileName.groovy:12), com.package.Foo$$DNLbVXwm.methodName(Unknown Source), *
        // 2.) [com.package.Foo$$ENLbVXwm.methodName(FileName.groovy:12), * com.springsource.loaded. *
        // Pattern below test this.

        //javaText.append("System.out.println(java.util.Arrays.toString(new Exception().getStackTrace()));\n");
        //javaText.append("System.out.println(\"\\\\[([^,()]+\\\\$\\\\$)[A-Za-z0-9]{8}(\\\\.[^,()]+)\\\\(" + s + ":\\\\d+\\\\), (\\\\1[A-Za-z0-9]{8}\\\\2\\\\(Unknown Source\\\\), |.+(?:com|org)\\\\.springsource\\\\.loaded\\\\.).+\")\n");

        javaText.append(
          "if (java.util.Arrays.toString(new Exception().getStackTrace()).matches(\"\\\\[([^,()]+\\\\$\\\\$)[A-Za-z0-9]{8}(\\\\.[^,()]+)\\\\(")
          .append(s)
          .append(":\\\\d+\\\\), (\\\\1[A-Za-z0-9]{8}\\\\2\\\\(Unknown Source\\\\), $OR$.+(?:com|org)\\\\.springsource\\\\.loaded\\\\.).+\")) {\n");
        javaText.append("  |thiz0 = thiz;\n");
        javaText.append(" } else {\n");
        javaText.append("  |thiz0 = this;\n");
        javaText.append(" }\n");
      }
    }

    if (!isStatic) {
      javaText.append("|clazz = |thiz0.getClass();\n");
      javaText.append("|mc = |thiz0.getMetaClass();\n");
    } else {
      assert contextClass != null;
      javaText.append("|clazz = java.lang.Class.forName(\"").append(contextClass.getQualifiedName()).append("\");\n");
      javaText.append("|mc = groovy.lang.GroovySystem.getMetaClassRegistry().getMetaClass(|clazz);\n");
    }

    javaText.append("final java.lang.ClassLoader |parentLoader = |clazz.getClassLoader();\n" +
                    "   final groovy.lang.GroovyClassLoader |loader = new groovy.lang.GroovyClassLoader(|parentLoader);\n" +
                    "   final java.lang.Class |c = |loader.parseClass(");
    javaText.append("\"" + IMPORTS + "class DUMMY").append(" { ").append("public groovy.lang.Closure ")
      .append(EVAL_NAME).append(" = {").append(TEXT).append("}}\"");
    javaText.append(", \"DUMMY.groovy\");\n" +
                    "   int |i;\n" +
                    "   java.lang.reflect.Field[] |fields = |c.getFields();\n" +
                    "   for (int |j = 0; |j < |fields.length; |j++) if (|fields[|j].getName().equals(\"_JETGROOVY_EVAL_\")) {|i = |j; break;}\n" +
                    "   final java.lang.reflect.Field |field = |fields[|i];\n" +
                    "   final java.lang.Object |closure = |field.get(|c.newInstance());\n");

    javaText.append("groovy.lang.ExpandoMetaClass |emc = new groovy.lang.ExpandoMetaClass(|clazz);\n");
    if (!isStatic) {
      javaText.append("|closure.setDelegate(|thiz0);\n");
      javaText.append("|emc.setProperty(\"").append(EVAL_NAME).append("\", |closure);\n");
    } else {
      javaText.append("|emc.getProperty(\"static\").setProperty(\"").append(EVAL_NAME).append("\", |closure);\n");
    }
    javaText.append("|emc.initialize();\n");
    javaText.append(unwrapVals(values));
    if (!isStatic) {
      javaText.append("java.lang.Object |res = ((groovy.lang.MetaClassImpl)|emc).invokeMethod(|thiz0, \"").append(EVAL_NAME).append("\", |resVals);\n");
    } else {
      javaText.append("java.lang.Object |res = ((groovy.lang.MetaClassImpl)|emc).invokeStaticMethod(|clazz, \"").append(EVAL_NAME).append("\", |resVals);\n");
    }
    javaText.append("if (|res instanceof java.lang.Boolean) ((java.lang.Boolean) |res).booleanValue() else |res");

    final PsiElementFactory factory = JavaPsiFacade.getInstance(toEval.getProject()).getElementFactory();

    String hiddenJavaVars = StringUtil.replace(javaText.toString(), "|", "_$_" + new Random().nextInt(42));
    hiddenJavaVars = hiddenJavaVars.replaceAll("\\$OR\\$", "|");
    final String finalText = StringUtil.replace(StringUtil.replace(hiddenJavaVars, TEXT, groovyText), IMPORTS, imports);
    JavaCodeFragment result = JavaCodeFragmentFactory.getInstance(project).createCodeBlockCodeFragment(finalText, null, true);
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
    final Map<GrExpression, String> replacements = new HashMap<GrExpression, String>();
    toEval.accept(new GroovyRecursiveElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        if (PsiUtil.isThisReference(referenceExpression) || PsiUtil.isSuperReference(referenceExpression)) {
          replaceWithReference(referenceExpression, "delegate");
          return;
        }

        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiMember && (resolved instanceof PsiClass || ((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC))) {
          String qName = StaticImportMethodFix.getMemberQualifiedName((PsiMember)resolved);
          if (qName != null && qName.contains(".") && !referenceExpression.isQualified()) {
            replaceWithReference(referenceExpression, qName);
            return;
          }
        }

        if (shouldDelegate(referenceExpression, resolved)) {
          replaceWithReference(referenceExpression, "delegate." + referenceExpression.getReferenceName());
          return;
        }

        if (resolved instanceof GrVariable && !(resolved instanceof GrField) && !PsiTreeUtil.isAncestor(toEval, resolved, false)) {
          final String name = ((GrVariable)resolved).getName();
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

      }

      private boolean shouldDelegate(GrReferenceExpression referenceExpression, @Nullable PsiElement resolved) {
        if (referenceExpression.isQualified()) {
          return false;
        }

        if (resolved instanceof GrField) {
          return true;
        }

        if (resolved instanceof PsiMethod && !referenceExpression.isQualified()) {
          String methodName = ((PsiMethod)resolved).getName();
          if (closure != null && "getDelegate".equals(methodName) || "call".equals(methodName)) {
            return true;
          }
        }

        return closure != null && resolved instanceof GrLightVariable && "owner".equals(((GrLightVariable)resolved).getName());
      }

      private void replaceWithReference(GrExpression expr, final String exprText) {
        replacements.put(expr, exprText);
      }

      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        super.visitCodeReferenceElement(refElement);
        if (refElement.getQualifier() == null) {
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

    for (GrExpression expression : replacements.keySet()) {
      expression.replaceWithExpression(factory.createExpressionFromText(replacements.get(expression)), false);
    }
    
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
      if (parent instanceof GroovyFile && parent.isPhysical()) return false;
      if (parent instanceof GrTypeDefinition) return false;
      if (parent instanceof GrClosableBlock) return false;

      parent = parent.getContext();
    }

    return false;
  }

  public boolean isContextAccepted(PsiElement context) {
    return context != null && context.getLanguage().equals(GroovyFileType.GROOVY_LANGUAGE);
  }

  public LanguageFileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return EvaluatorBuilderImpl.getInstance();
  }
}
