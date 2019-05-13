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
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementExploreUtil;
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementUtil;

public class GroovyImplementMethodsHandler implements LanguageCodeInsightActionHandler {
  @Override
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, true);
    if (aClass instanceof GrTypeDefinition) {
      GrTypeDefinition typeDefinition = (GrTypeDefinition)aClass;
      if (GroovyOverrideImplementExploreUtil.getMethodSignaturesToImplement(typeDefinition).isEmpty()) {
        HintManager.getInstance().showErrorHint(editor, "No methods to implement have been found");
        return;
      }

      GroovyOverrideImplementUtil.chooseAndImplementMethods(project, editor, typeDefinition);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
