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
package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 5.02.2014
 */
public class GradleEditorTabTitleProvider implements EditorTabTitleProvider, DumbAware {
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    if (GradleConstants.EXTENSION.equals(file.getExtension()) && GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName())) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        if (!module.isDisposed()) {
          for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
            if (root.equals(file.getParent())) {
              return module.getName();
            }
          }
        }
      }
    }

    return null;
  }
}