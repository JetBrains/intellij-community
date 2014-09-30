
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

public abstract class IdeDocumentHistory {
  public static IdeDocumentHistory getInstance(Project project) {
    return project.getComponent(IdeDocumentHistory.class);
  }

  public abstract void includeCurrentCommandAsNavigation();
  public abstract void includeCurrentPlaceAsChangePlace();
  public abstract void clearHistory();

  public abstract void back();
  public abstract void forward();

  public abstract boolean isBackAvailable();
  public abstract boolean isForwardAvailable();

  public abstract void navigatePreviousChange();
  public abstract void navigateNextChange();
  public abstract boolean isNavigatePreviousChangeAvailable();
  public abstract boolean isNavigateNextChangeAvailable();

  public abstract VirtualFile[] getChangedFiles();
}
