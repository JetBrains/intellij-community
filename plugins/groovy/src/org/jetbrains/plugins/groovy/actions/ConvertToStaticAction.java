/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.refactoring.convertToStatic.ConvertToStaticHandler;

public class ConvertToStaticAction extends BaseRefactoringAction {

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return isEnabledOnElements(new PsiElement[]{element});
  }

  @Override
  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return super.isEnabledOnDataContext(dataContext);
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return GroovyLanguage.INSTANCE == language;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return !ConvertToStaticHandler.collectFilesForProcessing(elements).isEmpty();
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new ConvertToStaticHandler();
  }
}
