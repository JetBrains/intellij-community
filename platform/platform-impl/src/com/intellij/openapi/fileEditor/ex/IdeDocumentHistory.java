
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
package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface IdeDocumentHistory {
  static IdeDocumentHistory getInstance(Project project) {
    return project.getComponent(IdeDocumentHistory.class);
  }

  void includeCurrentCommandAsNavigation();
  void includeCurrentPlaceAsChangePlace();
  void clearHistory();

  void back();
  void forward();

  boolean isBackAvailable();
  boolean isForwardAvailable();

  void navigatePreviousChange();
  void navigateNextChange();
  boolean isNavigatePreviousChangeAvailable();
  boolean isNavigateNextChangeAvailable();

  VirtualFile[] getChangedFiles();
  boolean isRecentlyChanged(@NotNull VirtualFile file);
}
