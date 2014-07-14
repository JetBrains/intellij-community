/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * Created by Max Medvedev on 8/24/13
 */
public class GrRemoveExplicitTypeDeclarationIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    PsiElement parent = element.getParent();

    if (parent instanceof GrVariable) {
      ((GrVariable)parent).setType(null);
    }
    else if (parent instanceof GrVariableDeclaration) {
      ((GrVariableDeclaration)parent).setType(null);
    }
    else if (parent instanceof GrMethod) {
      ((GrMethod)parent).setReturnType(null);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        PsiElement parent = element.getParent();
        if (element instanceof GrTypeElement || element instanceof GrModifierList) {
          return parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getTypeElementGroovy() != null ||
                 parent instanceof GrMethod && ((GrMethod)parent).getReturnTypeElementGroovy() != null;
        }

        if (parent instanceof GrNamedElement && ((GrNamedElement)parent).getNameIdentifierGroovy().equals(element)) {
          if (parent instanceof GrVariable) {
            return ((GrVariable)parent).getTypeElementGroovy() != null;
          }

          if (parent instanceof GrMethod && ((GrMethod)parent).findSuperMethods().length == 0 ) {
            return ((GrMethod)parent).getReturnTypeElementGroovy() != null;
          }
        }

        return false;
      }
    };
  }
}
