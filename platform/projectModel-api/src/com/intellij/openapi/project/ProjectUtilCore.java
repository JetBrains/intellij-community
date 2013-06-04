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
package com.intellij.openapi.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ProjectUtilCore {
  public static String displayUrlRelativeToProject(@NotNull VirtualFile file,
                                                   @NotNull String url,
                                                   @NotNull Project project,
                                                   boolean includeFilePath,
                                                   boolean keepModuleAlwaysOnTheLeft) {
    final VirtualFile baseDir = project.getBaseDir();
    if (baseDir != null && includeFilePath) {
      //noinspection ConstantConditions
      final String projectHomeUrl = baseDir.getPresentableUrl();
      if (url.startsWith(projectHomeUrl)) {
        url = "..." + url.substring(projectHomeUrl.length());
      }
    }

    if (SystemInfo.isMac && file.getFileSystem() instanceof LocalFileProvider) {
      final VirtualFile fileForJar = ((LocalFileProvider)file.getFileSystem()).getLocalVirtualFileFor(file);
      if (fileForJar != null) {
        final OrderEntry libraryEntry = LibraryUtil.findLibraryEntry(file, project);
        if (libraryEntry != null) {
          if (libraryEntry instanceof JdkOrderEntry) {
            url = url + " - [" + ((JdkOrderEntry)libraryEntry).getJdkName() + "]";
          }
          else {
            url = url + " - [" + libraryEntry.getPresentableName() + "]";
          }
        }
        else {
          url = url + " - [" + fileForJar.getName() + "]";
        }
      }
    }

    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) return url;
    return !keepModuleAlwaysOnTheLeft && SystemInfo.isMac ?
           url + " - [" + module.getName() + "]" :
           "[" + module.getName() + "] - " + url;
  }
}
