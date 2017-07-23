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
package com.intellij.openapi.wm;

import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.PlatformFrameTitleBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
 */
public class IdeaFrameTitleBuilder extends PlatformFrameTitleBuilder {
  @Override
  public String getFileTitle(@NotNull final Project project, @NotNull final VirtualFile file) {
    String fileTitle = EditorTabbedContainer.calcTabTitle(project, file);
    if (SystemInfo.isMac) return fileTitle;

    VirtualFile parent = file.getParent();
    if (parent == null || !fileTitle.endsWith(file.getPresentableName())) return fileTitle;

    String url = FileUtil.getLocationRelativeToUserHome(parent.getPresentableUrl() + File.separator + file.getName());
    return ProjectUtilCore.displayUrlRelativeToProject(file, url, project, !SystemInfo.isMac, false);
  }
}
