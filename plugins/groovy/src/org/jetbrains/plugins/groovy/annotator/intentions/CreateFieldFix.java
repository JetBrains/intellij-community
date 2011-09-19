/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.editor.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

/**
 * @author Maxim.Medvedev
 */
public class CreateFieldFix  {
  private final GrMemberOwner myTargetClass;

  protected GrMemberOwner getTargetClass() {
    return myTargetClass;
  }

  protected CreateFieldFix(GrMemberOwner targetClass) {
    myTargetClass = targetClass;
  }

  public boolean isAvailable() {
    return myTargetClass.isValid();
  }

  protected void doFix(Project project, String[] modifiers, String fieldName, TypeConstraint[] typeConstraints) throws IncorrectOperationException {
    PsiClassType type = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName("Object", GlobalSearchScope.allScope(project));
    GrVariableDeclaration fieldDecl =
      GroovyPsiElementFactory.getInstance(project).createFieldDeclaration(modifiers, fieldName, null, type);
    fieldDecl = (GrVariableDeclaration)myTargetClass.add(fieldDecl);
    GrTypeElement typeElement = fieldDecl.getTypeElementGroovy();
    assert typeElement != null;

    ChooseTypeExpression expr = new ChooseTypeExpression(typeConstraints, PsiManager.getInstance(project));
    TemplateBuilderImpl builder = new TemplateBuilderImpl(fieldDecl);
    builder.replaceElement(typeElement, expr);
    fieldDecl = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(fieldDecl);
    if (fieldDecl == null) {
      return;
    }

    Template template = builder.buildTemplate();

    Editor newEditor = QuickfixUtil.positionCursor(project, myTargetClass.getContainingFile(), fieldDecl);
    TextRange range = fieldDecl.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, template);
  }
}
