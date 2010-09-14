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

package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class ConvertClosureToMethodIntention extends Intention {
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    element = element.getParent();
    StringBuilder builder = new StringBuilder(element.getTextLength());
    final GrField field = (GrField)element;
    final GrClosableBlock block = (GrClosableBlock)field.getInitializerGroovy();

    final GrModifierList modifierList = field.getModifierList();
    if (modifierList.getModifiers().length > 0 || modifierList.getAnnotations().length > 0) {
      builder.append(modifierList.getText());
    }
    else {
      builder.append(GrModifier.DEF);
    }
    builder.append(' ').append(field.getName());

    builder.append('(');
    if (block.hasParametersSection()) {
      builder.append(block.getParameterList().getText());
    }
    else {
      builder.append("def it = null");
    }
    builder.append(") {");
    block.getParameterList().delete();
    block.getLBrace().delete();
    final PsiElement psiElement = PsiUtil.skipWhitespaces(block.getFirstChild(), true);
    if (psiElement != null && "->".equals(psiElement.getText())) {
      psiElement.delete();
    }
    builder.append(block.getText());
    final GrMethod method = GroovyPsiElementFactory.getInstance(element.getProject()).createMethodFromText(builder.toString());
    field.getParent().replace(method);
  }

  private static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) return false;
      if (((GrField)parent).getNameIdentifierGroovy() != element) return false;

      final PsiElement varDeclaration = parent.getParent();
      if (!(varDeclaration instanceof GrVariableDeclaration)) return false;
      if (((GrVariableDeclaration)varDeclaration).getVariables().length != 1) return false;

      final GrExpression expression = ((GrField)parent).getInitializerGroovy();
      return expression instanceof GrClosableBlock;
    }
  }
}
