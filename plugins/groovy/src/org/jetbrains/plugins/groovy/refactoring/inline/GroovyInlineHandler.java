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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitution;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author ilyas
 */
public class GroovyInlineHandler implements InlineHandler {

  private static final String INLINE_REFACTORING = GroovyRefactoringBundle.message("inline.refactoring.title");

  @Nullable
  public Settings prepareInlineElement(final PsiElement element, Editor editor, boolean invokedOnReference) {
    if (element instanceof GrVariable && GroovyRefactoringUtil.isLocalVariable((GrVariable) element)) {
      return GroovyInlineVariableUtil.inlineLocalVariableSettings((GrVariable) element, editor, invokedOnReference);
    } else if (element instanceof GrMethod) {
      return GroovyInlineMethodUtil.inlineMethodSettings((GrMethod) element, editor, invokedOnReference);
    } else {
      Application application = ApplicationManager.getApplication();
      if (!application.isUnitTestMode()) {
        String message = GroovyRefactoringBundle.message("wrong.element.to.inline");
        CommonRefactoringUtil.showErrorHint(element.getProject(), editor, message, INLINE_REFACTORING, HelpID.INLINE_VARIABLE);
      }
      if (element instanceof GrTypeDefinition || element instanceof GrClassSubstitution) {
        return null;      //todo inline to anonymous class, push members from super class
      }
    }

    if (element instanceof PsiMember) {
      //dummy, todo implement
      return new Settings() {
        public boolean isOnlyOneReferenceToInline() {
          return true;
        }
      };
    }
    return null;
  }

  public void removeDefinition(final PsiElement element, Settings settings) {
    final PsiElement owner = element.getParent().getParent();
    if (element instanceof GrVariable &&
        owner instanceof GrVariableDeclarationOwner) {
      ((GrVariableDeclarationOwner) owner).removeVariable(((GrVariable) element));
    }
    if (element instanceof GrMethod) {
      element.delete();
    }
  }

  @Nullable
  public Inliner createInliner(PsiElement element, Settings settings) {
    if (element instanceof GrVariable &&
        GroovyRefactoringUtil.isLocalVariable((GrVariable) element)) {
      return GroovyInlineVariableUtil.createInlinerForLocalVariable(((GrVariable) element));
    }
    if (element instanceof GrMethod) {
      return new GroovyMethodInliner((GrMethod) element);
    }
    return null;
  }

}

