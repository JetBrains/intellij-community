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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;


/**
 * @author Maxim.Medvedev
 */
public class ConvertMethodToClosureIntention extends Intention {
  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    element = element.getParent();
    final GrMethod method = (GrMethod)element;
    StringBuilder builder = new StringBuilder(method.getTextLength());
    String modifiers = method.getModifierList().getText();
    if (modifiers.trim().length() == 0) {
      modifiers = GrModifier.DEF;
    }
    builder.append(modifiers).append(' ');
    builder.append(method.getName()).append("={");
    builder.append(method.getParameterList().getText()).append(" ->");
    final GrOpenBlock block = method.getBlock();
    builder.append(block.getText().substring(1));
    final GrVariableDeclaration variableDeclaration =
      GroovyPsiElementFactory.getInstance(element.getProject()).createFieldDeclarationFromText(builder.toString());
    method.replace(variableDeclaration);
  }

  private static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      final PsiElement parent = element.getParent();
      return parent instanceof GrMethod &&
             element == ((GrMethod)parent).getNameIdentifierGroovy() &&
             ((GrMethod)parent).getBlock() != null &&
             parent.getParent() instanceof GrTypeDefinitionBody;
//      return element instanceof GrMethod && ((GrMethod)element).getBlock() != null && element.getParent() instanceof GrTypeDefinitionBody;
    }
  }
}


