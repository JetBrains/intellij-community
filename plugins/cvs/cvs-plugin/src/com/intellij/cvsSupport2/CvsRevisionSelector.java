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
package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.ui.SelectFileVersionDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class CvsRevisionSelector implements RevisionSelector {


  private final Project myProject;

  public CvsRevisionSelector(final Project project) {
    myProject = project;
  }

  @Override
  @Nullable public VcsRevisionNumber selectNumber(VirtualFile file) {
    final SelectFileVersionDialog selector = new SelectFileVersionDialog(
      VcsContextFactory.SERVICE.getInstance().createFilePathOn(file),
      myProject);

    if (selector.showAndGet()) {
      return new CvsRevisionNumber(selector.getRevisionOrDate());
    }
    else {
      return null;
    }
  }
}
