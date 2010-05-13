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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;

/**
 * @author yole
 */
public class PlatformFrameTitleBuilder extends FrameTitleBuilder {
  public String getProjectTitle(final Project project) {
    final VirtualFile baseDir = project.getBaseDir();
    if (baseDir != null) {
      return project.getName() + " - [" + baseDir.getPresentableUrl() + "]";
    }
    return project.getName();
  }

  public String getFileTitle(final Project project, final VirtualFile file) {
    String url = file.getPresentableUrl();
    VirtualFile baseDir = ProjectBaseDirectory.getInstance(project).getBaseDir();
    if (baseDir == null) baseDir = project.getBaseDir();
    if (baseDir != null) {
      //noinspection ConstantConditions
      final String projectHomeUrl = baseDir.getPresentableUrl();
      if (url.startsWith(projectHomeUrl)) {
        url = "..." + url.substring(projectHomeUrl.length());
      }
    }
    return url;
  }
}
