/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author irengrig
 *         Date: 2/10/11
 *         Time: 3:59 PM
 */
abstract class AbstractIgnoredFilesHolder implements FileHolder, IgnoredFilesHolder {
  protected final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  protected AbstractIgnoredFilesHolder(Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  protected abstract void removeFile(final VirtualFile file);
  protected abstract Collection<VirtualFile> keys();

  @Override
  public void cleanAndAdjustScope(final VcsModifiableDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;

        final Iterator<VirtualFile> iterator = keys().iterator();
        while (iterator.hasNext()) {
          final VirtualFile file = iterator.next();
          if (isFileDirty(scope, file)) {
            iterator.remove();
          }
        }
      }
    });
  }

  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    if (! file.isValid()) return true;
    final AbstractVcs vcsArr[] = new AbstractVcs[1];
    if (scope.belongsTo(new FilePathImpl(file), new Consumer<AbstractVcs>() {
      @Override
      public void consume(AbstractVcs vcs) {
        vcsArr[0] = vcs;
      }
    })) {
      return true;
    }

    return vcsArr[0] == null;
  }

  protected boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || myVcsManager.getVcsFor(file) == null;
  }
}
