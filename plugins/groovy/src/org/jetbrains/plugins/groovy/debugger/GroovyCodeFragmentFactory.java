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
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.Set;

/**
 * @author ven
 */
public class GroovyCodeFragmentFactory implements CodeFragmentFactory {
  public PsiCodeFragment createCodeFragment(TextWithImports textWithImports, PsiElement context, Project project) {
    String text = textWithImports.getText();
    String imports = textWithImports.getImports();
    GroovyPsiElement toEval;
    GroovyElementFactory factory = GroovyElementFactory.getInstance(project);
    toEval = textWithImports.getKind() == CodeFragmentKind.EXPRESSION ?
        factory.createExpressionFromText(text, context) : factory.createStatementFromText(text);

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

    StringBuffer javaText = new StringBuffer();
    javaText.append("java.util.Map m = new java.util.HashMap();\n");
    for (String local : locals) {
      javaText.append("m.put(\"").append(local).append("\", ").append(local).append(");\n");
    }
    javaText.append("groovy.lang.Binding b = new groovy.lang.Binding(m);\n");
    String finalEvalText = imports + "\n" + text;
    javaText.append("new groovy.lang.GroovyShell(b).evaluate(\"").append(StringUtil.escapeStringCharacters(finalEvalText)).append("\");");

    return toEval.getManager().getElementFactory().createCodeBlockCodeFragment(javaText.toString(), null, true);
  }

  public boolean isContextAccepted(PsiElement context) {
    return context.getLanguage().equals(GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  public String getDisplayName() {
    return "Groovy";
  }
}
