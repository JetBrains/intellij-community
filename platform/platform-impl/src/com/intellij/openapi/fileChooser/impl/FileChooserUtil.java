/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FileChooserUtil {
  private static final String LAST_OPENED_FILE_PATH = "last_opened_file_path";

  @Nullable
  public static VirtualFile getLastOpenedFile(@Nullable final Project project) {
    if (project != null) {
      final String path = PropertiesComponent.getInstance(project).getValue(LAST_OPENED_FILE_PATH);
      if (path != null) {
        return LocalFileSystem.getInstance().findFileByPath(path);
      }
    }
    return null;
  }

  public static void setLastOpenedFile(@Nullable final Project project, @Nullable final VirtualFile file) {
    if (project != null && !project.isDisposed() && file != null) {
      PropertiesComponent.getInstance(project).setValue(LAST_OPENED_FILE_PATH, file.getPath());
    }
  }

  @Nullable
  public static VirtualFile getFileToSelect(@NotNull FileChooserDescriptor descriptor, @Nullable Project project,
                                            @Nullable VirtualFile toSelect, @Nullable VirtualFile lastPath) {
    boolean chooseDir = descriptor instanceof FileSaverDescriptor;
    VirtualFile result;

    if (toSelect == null && lastPath == null) {
      result = project == null? null : project.getBaseDir();
    }
    else if (toSelect != null && lastPath != null) {
      if (Boolean.TRUE.equals(descriptor.getUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT))) {
        result = lastPath;
      }
      else {
        result = toSelect;
      }
    }
    else if (toSelect == null) {
      result = lastPath;
    }
    else {
      result = toSelect;
    }

    if (result != null) {
      if (chooseDir && !result.isDirectory()) {
        result = result.getParent();
      }
    }
    else if (SystemInfo.isUnix) {
      result = VfsUtil.getUserHomeDir();
    }

    return result;
  }

  @NotNull
  public static List<VirtualFile> getChosenFiles(@NotNull final FileChooserDescriptor descriptor,
                                                 @NotNull final List<VirtualFile> selectedFiles) {
    return ContainerUtil.mapNotNull(selectedFiles, new NullableFunction<VirtualFile, VirtualFile>() {
      @Override
      public VirtualFile fun(final VirtualFile file) {
        return file != null && file.isValid() ? descriptor.getFileToSelect(file) : null;
      }
    });
  }
}
