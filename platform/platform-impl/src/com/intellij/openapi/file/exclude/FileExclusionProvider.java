/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Rustam Vishnyakov
 */
public abstract class FileExclusionProvider {

  public static ExtensionPointName<FileExclusionProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileExclusionProvider");
  public static ExtensionPointName<FileExclusionProvider> PROJECT_EP_NAME =
    ExtensionPointName.create("com.intellij.projectFileExclusionProvider");

  public boolean isFileExcluded(final VirtualFile file) {
    return false;
  }

  public boolean isFileNameExcluded(final String name) {
    return false;
  }

  public static boolean isExcluded(final @Nullable Project project, final VirtualFile file) {
    if (file.isDirectory()) return false;
    for (FileExclusionProvider exclusionProvider : Extensions.getExtensions(EP_NAME)) {
      if (exclusionProvider.isFileExcluded(file)) return true;
    }
    if (project != null) {
      for (FileExclusionProvider exclusionProvider : Extensions.getExtensions(PROJECT_EP_NAME, project)) {
        if (exclusionProvider.isFileExcluded(file)) return true;
      }
    }
    else {
      for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
        assert openProject != null;
        if(isExcluded(openProject, file)) return true;
      }
    }
    return false;
  }

  public static boolean isExcluded (final String fileName) {
    for (FileExclusionProvider exclusionProvider : Extensions.getExtensions(EP_NAME)) {
      if (exclusionProvider.isFileNameExcluded(fileName)) return true;
    }
    return false;
  }

}
