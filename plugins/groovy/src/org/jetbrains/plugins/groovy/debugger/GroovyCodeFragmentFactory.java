/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Set;

/**
 * @author ven
 */
public class GroovyCodeFragmentFactory implements CodeFragmentFactory {
  private static final String EVAL_NAME = "_JETGROOVY_EVAL_";

  private String createProperty(String text, String imports, String[] names) {
    String classText = "\"" + imports + "class DUMMY { public groovy.lang.Closure " + EVAL_NAME + " = {" + getCommaSeparatedNamesList(names) + "->" + text + "}}\"";

    return "final java.lang.ClassLoader parentLoader = this.getClass().getClassLoader();\n" +
        "   final groovy.lang.GroovyClassLoader loader = new groovy.lang.GroovyClassLoader(parentLoader);\n" +
        "   final java.lang.Class c = loader.parseClass(" + classText + ", \"DUMMY.groovy\");\n" +
        "   int i;\n" +
        "   java.lang.reflect.Field[] fields = c.getFields();\n" +
        "   for (int j = 0; j < fields.length; j++) if (fields[j].getName().equals(\"_JETGROOVY_EVAL_\")) {i = j; break;}\n" +
        "   final java.lang.reflect.Field field = fields[i];\n" +
        "   final java.lang.Object closure = field.get(c.newInstance())";
  }

  public PsiCodeFragment createCodeFragment(TextWithImports textWithImports, PsiElement context, Project project) {
    String text = textWithImports.getText();
    String imports = textWithImports.getImports();
    final GroovyPsiElement toEval;
    GroovyElementFactory factory = GroovyElementFactory.getInstance(project);
    toEval = factory.createGroovyFile(text, false, context);

    final Set<String> namesList = new HashSet<String>();
    toEval.accept(new GroovyRecursiveElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof GrVariable && !(resolved instanceof GrField) &&
            !PsiTreeUtil.isAncestor(toEval, resolved, false)) {
          namesList.add(((GrVariable) resolved).getName());
        }
      }
    });

    String[] names = namesList.toArray(new String[namesList.size()]);

    PsiClass contextClass = getContextClass(context);
    boolean isStatic = isStaticContext(context);
    StringBuffer javaText = new StringBuffer();

    javaText.append(createProperty(StringUtil.escapeStringCharacters(text), imports, names));
    javaText.append("groovy.lang.MetaClass mc = ((groovy.lang.GroovyObject)this).getMetaClass();\n");
    javaText.append("groovy.lang.ExpandoMetaClass emc = new groovy.lang.ExpandoMetaClass(this.getClass());\n");
    javaText.append("emc.setProperty(\"").append(EVAL_NAME).append("\", closure);\n");
    javaText.append("((groovy.lang.GroovyObject)this).setMetaClass(emc);\n");
    javaText.append("emc.initialize();\n");
    javaText.append("Object res = ((groovy.lang.MetaClassImpl)((groovy.lang.GroovyObject)this).getMetaClass()).invokeMethod(this, \"").append(EVAL_NAME).append("\", new Object[]{").
        append(getCommaSeparatedNamesList(names)).append("});\n");
    javaText.append("((groovy.lang.GroovyObject)this).setMetaClass(mc);"); //try/finally is not supported
    javaText.append("res");

    PsiElementFactory elementFactory = toEval.getManager().getElementFactory();
    PsiCodeFragment result = elementFactory.createCodeBlockCodeFragment(javaText.toString(), null, true);
    result.setThisType(elementFactory.createType(contextClass));
    return result;
  }

  public PsiCodeFragment createPresentationCodeFragment(TextWithImports item, PsiElement context, Project project) {
    return GroovyElementFactory.getInstance(project).createGroovyFile(item.getText(), true, context);
  }

  private String getCommaSeparatedNamesList(String[] names) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < names.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(names[i]);
    }
    return buffer.toString();
  }

  private PsiClass getContextClass(PsiElement context) {
    GroovyPsiElement parent = PsiTreeUtil.getParentOfType(context, GrTypeDefinition.class, GroovyFile.class);
    if (parent instanceof GrTypeDefinition) return (PsiClass) parent;
    else if (parent instanceof GroovyFile) return ((GroovyFile) parent).getScriptClass();
    return null;
  }

  private boolean isStaticContext(PsiElement context) {
    PsiElement parent = context;
    while (parent != null) {
      if (parent instanceof PsiModifierListOwner && ((PsiModifierListOwner) parent).hasModifierProperty(PsiModifier.STATIC)) return true;
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
