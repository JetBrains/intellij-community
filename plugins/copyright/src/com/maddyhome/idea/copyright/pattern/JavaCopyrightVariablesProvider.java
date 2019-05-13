/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.maddyhome.idea.copyright.pattern;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class JavaCopyrightVariablesProvider extends CopyrightVariablesProvider {
  @Override
  public void collectVariables(@NotNull Map<String, Object> context, Project project, Module module, @NotNull final PsiFile file) {
    if (file instanceof PsiClassOwner) {
      final FileInfo info = new FileInfo(file) {
        @Override
        public String getClassName() {
          if (file instanceof PsiJavaFile) {
            return ((PsiJavaFile)file).getClasses()[0].getName();
          }
          else {
            return super.getClassName();
          }
        }

        @Override
        public String getQualifiedClassName() {
          if (file instanceof PsiJavaFile) {
            return ((PsiJavaFile)file).getClasses()[0].getQualifiedName();
          } else {
            return super.getQualifiedClassName();
          }
        }
      };
      context.put("file", info);
    }
  }
}
