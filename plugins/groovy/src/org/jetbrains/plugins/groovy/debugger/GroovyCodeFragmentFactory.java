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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
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

import java.util.Set;

/**
 * @author ven
 */
public class GroovyCodeFragmentFactory implements CodeFragmentFactory {
  private static final String EVAL_NAME = "_JETGROOVY_EVAL_";

  private static String createProperty(String text, String imports, String[] names) {
    String classText = "\"" +
                       imports +
                       "class DUMMY { " +
                       "public groovy.lang.Closure " +
                       EVAL_NAME +
                       " = {" +
                       getCommaSeparatedNamesList(names) +
                       "->" +
                       text +
                       "}}\"";

    return "final java.lang.ClassLoader parentLoader = clazz.getClassLoader();\n" +
           "   final groovy.lang.GroovyClassLoader loader = new groovy.lang.GroovyClassLoader(parentLoader);\n" +
           "   final java.lang.Class c = loader.parseClass(" +
           classText +
           ", \"DUMMY.groovy\");\n" +
           "   int i;\n" +
           "   java.lang.reflect.Field[] fields = c.getFields();\n" +
           "   for (int j = 0; j < fields.length; j++) if (fields[j].getName().equals(\"_JETGROOVY_EVAL_\")) {i = j; break;}\n" +
           "   final java.lang.reflect.Field field = fields[i];\n" +
           "   final java.lang.Object closure = field.get(c.newInstance());\n";
  }

  private static String unwrapVals(String[] vals) {
    StringBuilder sb = new StringBuilder();
    sb.append("java.util.ArrayList resList = new java.util.ArrayList();\n");
    sb.append("java.lang.Object[] resVals = new java.lang.Object[").append(vals.length).append("];\n");
    sb.append("java.lang.Object o;\n");
    for (int i = 0; i < vals.length; i++) {
      String s = vals[i];
      sb.append("  o = ").append(s).append(";\n");
      sb.append("  if (o instanceof groovy.lang.Reference) {o = ((groovy.lang.Reference)o).get();}\n");
      sb.append("  resVals[").append(i).append("] = o;\n");
    }
    return sb.toString();
  }

  public JavaCodeFragment createCodeFragment(TextWithImports textWithImports, PsiElement context, Project project) {
    String text = textWithImports.getText();
    String imports = textWithImports.getImports();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final GroovyFile toEval = factory.createGroovyFile(text, false, context);
    final Set<String> namesList = new HashSet<String>();
    final GrClosableBlock closure = PsiTreeUtil.getParentOfType(context, GrClosableBlock.class);

    final Set<String> valList = new HashSet<String>();
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
          namesList.add(name);
          if (closure != null &&
              PsiTreeUtil.findCommonParent(resolved, closure) != closure &&
              !(resolved instanceof ClosureSyntheticParameter)) {
            // Evaluating inside closure for outer variable definitions
            // All non-local variables are accessed by references
            valList.add("this." + name);
          } else {
            valList.add(name);
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

    text = toEval.getText();

    String[] names = ArrayUtil.toStringArray(namesList);
    String[] vals = ArrayUtil.toStringArray(valList);

    PsiClass contextClass = PsiUtil.getContextClass(context);
    boolean isStatic = isStaticContext(context);
    StringBuffer javaText = new StringBuffer();

    javaText.append("groovy.lang.MetaClass mc;\n");
    javaText.append("java.lang.Class clazz;\n");
    if (!isStatic) {
      javaText.append("clazz = ((java.lang.Object)this).getClass();\n");
      javaText.append("mc = ((groovy.lang.GroovyObject)this).getMetaClass();\n");
    } else {
      javaText.append("clazz = java.lang.Class.forName(\"").append(contextClass.getQualifiedName()).append("\");\n");
      javaText.append("mc = groovy.lang.GroovySystem.getMetaClassRegistry().getMetaClass(clazz);\n");
    }

    javaText.append(createProperty(stripImports(text, toEval), imports, names));

    javaText.append("groovy.lang.ExpandoMetaClass emc = new groovy.lang.ExpandoMetaClass(clazz);\n");
    if (!isStatic) {
      javaText.append("emc.setProperty(\"").append(EVAL_NAME).append("\", closure);\n");
      javaText.append("((groovy.lang.GroovyObject)this).setMetaClass(emc);\n");
    } else {
      javaText.append("((groovy.lang.GroovyObject)emc.getProperty(\"static\")).setProperty(\"").append(EVAL_NAME).append("\", closure);\n");
      javaText.append("groovy.lang.GroovySystem.getMetaClassRegistry().setMetaClass(clazz, emc);\n");
    }
    javaText.append("emc.initialize();\n");
    javaText.append(unwrapVals(vals));
    if (!isStatic) {
      javaText.append("java.lang.Object res = ((groovy.lang.MetaClassImpl)emc).invokeMethod(this, \"").append(EVAL_NAME).append("\", ").
        append("resVals").append(");\n");
      javaText.append("((groovy.lang.GroovyObject)this).setMetaClass(mc);"); //try/finally is not supported
    } else {
      javaText.append("java.lang.Object res = ((groovy.lang.MetaClassImpl)emc).invokeStaticMethod(clazz, \"").append(EVAL_NAME)
        .append("\", ").
        append("resVals").append(");\n");
      javaText.append("groovy.lang.GroovySystem.getMetaClassRegistry().setMetaClass(clazz, mc);\n");
    }
    javaText.append("res");

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(toEval.getProject()).getElementFactory();
    JavaCodeFragment result = elementFactory.createCodeBlockCodeFragment(javaText.toString(), null, true);
    if (contextClass != null) {
      result.setThisType(elementFactory.createType(contextClass));
    }
    return result;
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

  private static String getCommaSeparatedNamesList(String[] names) {
    return StringUtil.join(names, ",");
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
