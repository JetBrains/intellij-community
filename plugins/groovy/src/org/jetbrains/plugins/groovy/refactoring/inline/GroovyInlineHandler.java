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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.Collection;

/**
 * @author ilyas
 */
public class GroovyInlineHandler implements InlineHandler {

  private static final String REFACTORING_NAME = GroovyRefactoringBundle.message("inline.variable.title");

  @Nullable
  public Settings prepareInlineElement(final PsiElement element, Editor editor, boolean invokedOnReference) {
    if (!invokedOnReference) {
      return null;
    }
    if (element instanceof GrVariable) { // todo add method && class
      return inlineLocalVariableSettings((GrVariable) element);
    }
    return null;
  }

  private Settings inlineLocalVariableSettings(final GrVariable element) {
    final Collection<PsiReference> refs = ReferencesSearch.search(element, GlobalSearchScope.projectScope(element.getProject()), false).findAll();
    if (refs.isEmpty()) {
      String message = GroovyRefactoringBundle.message("variable.is.never.used.0", element);
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INLINE_VARIABLE, element.getProject());
      return null;
    }
    return new Settings() {
      public boolean isOnlyOneReferenceToInline() {
        return refs.size() == 1 && refs.iterator().next().isReferenceTo(element);
      }
    };
  }

  public void removeDefinition(PsiElement element) {
  }

  @Nullable
  public Inliner createInliner(PsiElement element) {
    return null;
  }
}

