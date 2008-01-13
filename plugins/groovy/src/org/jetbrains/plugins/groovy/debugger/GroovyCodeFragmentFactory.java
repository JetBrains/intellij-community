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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
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
  private static final String EVAL_PROPERTY_NAME = "JETGROOVY_EVAL_PROP_NAME";

  public PsiCodeFragment createCodeFragment(TextWithImports textWithImports, PsiElement context, Project project) {
    String text = textWithImports.getText();
    String imports = textWithImports.getImports();
    GroovyPsiElement toEval;
    GroovyElementFactory factory = GroovyElementFactory.getInstance(project);
    toEval = factory.createGroovyFile(text, false, context);

    final Set<String> locals = new HashSet<String>();
    toEval.accept(new GroovyRecursiveElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof GrVariable && !(resolved instanceof GrField)) {
          locals.add(((GrVariable) resolved).getName());
        }
      }
    });

    PsiClass contextClass = getContextClass(context);
    assert contextClass != null;
    StringBuffer javaText = new StringBuffer();
    javaText.append("java.util.Map m = org.codehaus.groovy.runtime.DefaultGroovyMethods.getProperties(this);\n");
    for (String local : locals) {
      javaText.append("m.put(\"").append(local).append("\", ").append(local).append(");\n");
    }
    javaText.append("groovy.lang.Binding b = new groovy.lang.Binding(m);\n");
    String text1 = "def " + EVAL_PROPERTY_NAME + " = { " + text + "}";

    String finalEvalText = imports + "\n" + text1;
    javaText.append("groovy.lang.Script s = new groovy.lang.GroovyShell(b).parse(\"").
        append(StringUtil.escapeStringCharacters(finalEvalText)).append("\");\n");
    javaText.append("((groovy.lang.GroovyObject)this).setProperty(\"").append(EVAL_PROPERTY_NAME).append("\", s.getProperty(\"").append(EVAL_PROPERTY_NAME).append("\"));\n");
    javaText.append("((groovy.lang.GroovyObject)this).invokeMethod(\"").append(EVAL_PROPERTY_NAME).append("\", new Object[0])");

    PsiElementFactory elementFactory = toEval.getManager().getElementFactory();
    PsiCodeFragment result = elementFactory.createCodeBlockCodeFragment(javaText.toString(), null, true);
    result.setThisType(elementFactory.createType(contextClass));
    return result;
  }

  private PsiClass getContextClass(PsiElement context) {
    GroovyPsiElement parent = PsiTreeUtil.getParentOfType(context, GrTypeDefinition.class, GroovyFile.class);
    if (parent instanceof GrTypeDefinition) return (PsiClass) parent;
    else if (parent instanceof GroovyFile) return ((GroovyFile) parent).getScriptClass();
    return null;
  }

  public boolean isContextAccepted(PsiElement context) {
    return context != null && context.getLanguage().equals(GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  public String getDisplayName() {
    return "Groovy";
  }
}
