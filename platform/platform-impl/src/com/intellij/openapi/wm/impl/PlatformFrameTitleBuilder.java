/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.platform.ProjectBaseDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformFrameTitleBuilder extends FrameTitleBuilder {
  @Override
  public String getProjectTitle(@NotNull final Project project) {
    final String basePath = project.getBasePath();
    if (basePath == null) return project.getName();

    if (basePath.equals(project.getName())) {
      return "[" + FileUtil.getLocationRelativeToUserHome(basePath) + "]";
    }
    else {
      return project.getName() + " - [" + FileUtil.getLocationRelativeToUserHome(basePath) + "]";
    }
  }

  @Override
  public String getFileTitle(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (SystemInfo.isMac) {
      return UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file);
    }

    if (file instanceof VirtualFilePathWrapper) {
      return ((VirtualFilePathWrapper)file).getPresentablePath();
    }

    String url = FileUtil.getLocationRelativeToUserHome(file.getPresentableUrl());
    if (url == null) url = file.getPresentableUrl();

    VirtualFile baseDir = ProjectBaseDirectory.getInstance(project).getBaseDir();
    if (baseDir == null) baseDir = project.getBaseDir();

    if (baseDir != null) {
      final String projectHomeUrl = FileUtil.getLocationRelativeToUserHome(baseDir.getPresentableUrl());
      if (projectHomeUrl != null && url.startsWith(projectHomeUrl)) {
        url = "..." + url.substring(projectHomeUrl.length());
      }
    }

    return url;
  }
}
