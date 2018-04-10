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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public abstract class VcsPathPresenter {
  public static VcsPathPresenter getInstance(Project project) {
    return ServiceManager.getService(project, VcsPathPresenter.class);
  }

  /**
   * Returns the user-visible relative path from the content root under which the
   * specified file is located to the file itself, prefixed by the module name in
   * angle brackets.
   *
   * @param file the file for which the path is requested.
   * @return the relative path.
   */
  public abstract String getPresentableRelativePathFor(VirtualFile file);

  public abstract String getPresentableRelativePath(ContentRevision fromRevision, ContentRevision toRevision);
}
