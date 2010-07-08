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
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.09.2007
 */
public class GroovyImplementMethodsHandler implements LanguageCodeInsightActionHandler {
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    GroovyOverrideImplementUtil.invokeOverrideImplement(editor, file, true);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
