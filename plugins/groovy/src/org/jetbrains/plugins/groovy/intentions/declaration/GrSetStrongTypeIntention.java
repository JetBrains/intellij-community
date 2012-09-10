/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class GrSetStrongTypeIntention extends Intention {


  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    if (element instanceof GrVariableDeclaration) {
      GrVariable[] variables = ((GrVariableDeclaration)element).getVariables();
      ArrayList<TypeConstraint> types = new ArrayList<TypeConstraint>();
      for (GrVariable variable : variables) {
        if (variable.getInitializerGroovy() != null) {
          PsiType type = variable.getInitializerGroovy().getType();
          types.add(SupertypeConstraint.create(type));
        }
      }

      TemplateBuilderImpl builder = new TemplateBuilderImpl(element);


      PsiManager manager = element.getManager();

      GrModifierList modifierList = ((GrVariableDeclaration)element).getModifierList();

      PsiElement replaceElement;
      if (modifierList.hasModifierProperty(GrModifier.DEF) && modifierList.getModifiers().length == 1) {
        replaceElement = PsiUtil.findModifierInList(modifierList, GrModifier.DEF);
      }
      else {
        ((GrVariableDeclaration)element).setType(TypesUtil.createType("Abc", element));
        replaceElement = ((GrVariableDeclaration)element).getTypeElementGroovy();
      }
      assert replaceElement != null;
      TypeConstraint[] constraints = types.toArray(new TypeConstraint[types.size()]);
      ChooseTypeExpression chooseTypeExpression = new ChooseTypeExpression(constraints, manager, replaceElement.getResolveScope());
      builder.replaceElement(replaceElement, chooseTypeExpression);


      final PsiElement afterPostprocess = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(element);
      final Template template = builder.buildTemplate();
      TextRange range = afterPostprocess.getTextRange();
      Document document = editor.getDocument();
      document.deleteString(range.getStartOffset(), range.getEndOffset());

      TemplateManager templateManager = TemplateManager.getInstance(project);
      templateManager.startTemplate(editor, template);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (element instanceof GrVariableDeclaration && ((GrVariableDeclaration)element).getTypeElementGroovy() == null) {
          GrVariable[] variables = ((GrVariableDeclaration)element).getVariables();
          for (GrVariable variable : variables) {
            if (variable.getInitializerGroovy() != null) return true;
          }
        }

        return false;
      }
    };
  }
}
