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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.impl.FileIndexImplUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;

public class CommonCheckinFilesAction extends AbstractCommonCheckinAction {
  protected String getActionName(VcsContext dataContext) {
    FilePath[] roots = getRoots(dataContext);
    if (roots == null || roots.length == 0) return getCheckinActionName(dataContext);
    FilePath first = roots[0];
    if (roots.length == 1) {
      if (first.isDirectory()) {
        return VcsBundle.message("action.name.checkin.directory", getCheckinActionName(dataContext));
      }
      else {
        return VcsBundle.message("action.name.checkin.file", getCheckinActionName(dataContext));
      }
    }
    else {
      if (first.isDirectory()) {
        return VcsBundle.message("action.name.checkin.directories", getCheckinActionName(dataContext));
      }
      else {
        return VcsBundle.message("action.name.checkin.files", getCheckinActionName(dataContext));
      }
    }
  }

  @Override
  protected LocalChangeList getInitiallySelectedChangeList(final VcsContext context, final Project project) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    FilePath[] roots = getRoots(context);
    for(FilePath root: roots) {
      final VirtualFile file = root.getVirtualFile();
      if (file == null) continue;
      final Ref<Change> change = new Ref<Change>();
      if (!file.isDirectory()) {
        change.set(changeListManager.getChange(file));
      }
      else {
        final ExcludedFileIndex index = ExcludedFileIndex.getInstance(project);
        final VirtualFileFilter filter = new VirtualFileFilter() {
          public boolean accept(final VirtualFile file) {
            return (! index.isExcludedFile(file));
          }
        };
        FileIndexImplUtil.iterateRecursively(file, filter, new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            Change c = changeListManager.getChange(fileOrDir);
            if (c != null) {
              change.set(c);
              return false;
            }
            return true;
          }
        });
      }
      if (!change.isNull()) {
        return changeListManager.getChangeList(change.get());
      }
    }

    return changeListManager.getDefaultChangeList();
  }

  private String getCheckinActionName(VcsContext dataContext) {
    Project project = dataContext.getProject();
    if (project == null) return VcsBundle.message("vcs.command.name.checkin");

    AbstractVcs vcs = getCommonVcsFor(getRoots(dataContext), project);
    if (vcs == null) {
      return VcsBundle.message("vcs.command.name.checkin");
    }
    else {
      final CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment == null) {
        return VcsBundle.message("vcs.command.name.checkin");
      }
      return checkinEnvironment.getCheckinOperationName();
    }
  }

  @Override
  protected boolean approximatelyHasRoots(VcsContext dataContext) {
    return dataContext.getSelectedFilePaths().length > 0;
  }

  protected FilePath[] getRoots(VcsContext context) {
    return context.getSelectedFilePaths();
  }

  protected boolean filterRootsBeforeAction() {
    return true;
  }
}
