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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrRenameableLightElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

/**
 * @author Maxim.Medvedev
 */
public class GrLightElementRenamer extends RenamePsiElementProcessor {

  @Override
  public PsiElement substituteElementToRename(PsiElement element, @Nullable Editor editor) {
    String message =
      RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("rename.is.not.applicable.to.implicit.elements"));
    CommonRefactoringUtil.showErrorHint(element.getProject(), editor, message, RefactoringBundle.message("rename.title"), null);
    return null;
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (!(element instanceof LightElement)) return false;
    if (element instanceof GrRenameableLightElement) return false;
    final Language language = element.getLanguage();
    return GroovyFileType.GROOVY_LANGUAGE.equals(language);
  }
}
