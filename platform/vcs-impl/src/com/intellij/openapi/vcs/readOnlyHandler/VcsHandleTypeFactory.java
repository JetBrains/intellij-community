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
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class VcsHandleTypeFactory implements HandleTypeFactory {
  private final Project myProject;

  public VcsHandleTypeFactory(final Project project) {
    myProject = project;
  }

  @Nullable
  public HandleType createHandleType(final VirtualFile file) {
    if (! myProject.isInitialized()) return null;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
    if (vcs != null) {
      boolean fileExistsInVcs = vcs.fileExistsInVcs(new FilePathImpl(file));
      if (fileExistsInVcs && vcs.getEditFileProvider() != null) {
        return new VcsHandleType(vcs);
      }
    }
    return null;
  }
}
