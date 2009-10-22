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
package com.intellij.execution.testframework.sm;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class TestsLocationProviderUtil {
  @NonNls private static final String PROTOCOL_SEPARATOR = "://";

  private TestsLocationProviderUtil() {
  }

  @Nullable
  public static String extractProtocol(@NotNull final String locationUrl) {
    final int index = locationUrl.indexOf(PROTOCOL_SEPARATOR);
    if (index >= 0) {
      return locationUrl.substring(0, index);
    }
    return null;
  }

  @Nullable
  public static String extractPath(@NotNull final String locationUrl) {
    final int index = locationUrl.indexOf(PROTOCOL_SEPARATOR);
    if (index >= 0) {
      return locationUrl.substring(index + PROTOCOL_SEPARATOR.length());
    }
    return null;
  }

  public static List<VirtualFile> findSuitableFilesFor(final String filePath, final Project project) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

    // at first let's try to find file as is, by it's real path
    // and check that file belongs to current project
    // this location provider designed for tests thus we will check only project content
    // (we cannot check just sources or tests folders because RM doesn't use it
    final VirtualFile file = getByFullPath(filePath);
    final boolean inProjectContent = file != null && (index.isInContent(file));

    if (inProjectContent) {
      return Collections.singletonList(file);
    }

    //TODO: split file by "/" in parts
    final List<String> folders = new ArrayList<String>();
    //TODO:
    final String fileName = "foo.feature";

    //otherwise let's find all files with the same name and similar relative path
    final List<NavigationItem> items = new ArrayList<NavigationItem>();
    final ChooseByNameContributor[] contributors = Extensions.getExtensions(ChooseByNameContributor.FILE_EP_NAME);
    for (ChooseByNameContributor contributor : contributors) {
      // let's find files with same name in project and libraries
      final NavigationItem[] navigationItems = contributor.getItemsByName(fileName, fileName, project, true);
      for (NavigationItem navigationItem : navigationItems) {
        if (navigationItem instanceof PsiFile) {
          items.add(navigationItem);
        }
      }
    }
    //TODO let's filter psi files ...

    //TODO let's iterate relative path components and determine which files are closer to our relative path
    // let's extract the closest files to relative path. For this we will sort items by distance and
    // we also assume that relative files and folders should have at least one common parent folder - just to remove false positives on some cases
    for (String folder : folders) {

    }

    return Collections.emptyList();
  }

  @Nullable
  private static VirtualFile getByFullPath(String filePath) {
    final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (fileByPath != null) {
      return fileByPath;
    }
    // if we are in UnitTest mode probably TempFileSystem is used instead of LocaFileSystem
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final VirtualFile tempFileByPath = TempFileSystem.getInstance().findFileByPath(filePath);
      return tempFileByPath;
    }
    return null;
  }
}
