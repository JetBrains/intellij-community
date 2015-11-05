/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.internal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

public class IconsGeneratedSourcesFilter extends GeneratedSourcesFilter {
  @Override
  public boolean isGeneratedSource(@NotNull final VirtualFile file, @NotNull final Project project) {
    if (file.getName().endsWith("Icons.java")) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (psiFile instanceof PsiJavaFile) {
            for (PsiClass aClass : ((PsiJavaFile)psiFile).getClasses()) {
              if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                PsiDocComment comment = aClass.getDocComment();
                return comment != null && comment.getText().contains("run build/scripts/icons.gant instead");
              }
            }
          }
          return false;
        }
      });
    }
    return false;
  }
}
