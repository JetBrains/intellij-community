/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;

public class CommonCheckinFilesAction extends AbstractCommonCheckinAction {
  protected String getActionName(final VcsContext dataContext) {
    final String checkinActionName = getCheckinActionName(dataContext);
    return modifyCheckinActionName(dataContext, checkinActionName);
  }

  private String modifyCheckinActionName(final VcsContext dataContext, String checkinActionName) {
    final FilePath[] roots = getRoots(dataContext);
    if (roots == null || roots.length == 0) return checkinActionName;
    final FilePath first = roots[0];
    if (roots.length == 1) {
      if (first.isDirectory()) {
        return VcsBundle.message("action.name.checkin.directory", checkinActionName);
      }
      else {
        return VcsBundle.message("action.name.checkin.file", checkinActionName);
      }
    }
    else {
      if (first.isDirectory()) {
        return VcsBundle.message("action.name.checkin.directories", checkinActionName);
      }
      else {
        return VcsBundle.message("action.name.checkin.files", checkinActionName);
      }
    }
  }

  @Override
  protected String getMnemonicsFreeActionName(VcsContext context) {
    return modifyCheckinActionName(context, VcsBundle.message("vcs.command.name.checkin.no.mnemonics"));
  }

  @Override
  protected LocalChangeList getInitiallySelectedChangeList(final VcsContext context, final Project project) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    final FilePath[] roots = getRoots(context);
    for(final FilePath root: roots) {
      final VirtualFile file = root.getVirtualFile();
      if (file == null) continue;
      final Ref<Change> change = new Ref<Change>();
      if (!file.isDirectory()) {
        change.set(changeListManager.getChange(file));
      }
      else {
        final FileIndexFacade index = PeriodicalTasksCloser.getInstance().safeGetService(project, FileIndexFacade.class);
        final VirtualFileFilter filter = new VirtualFileFilter() {
          public boolean accept(final VirtualFile file) {
            return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
              @Override
              public Boolean compute() {
                return (! index.isExcludedFile(file));
              }
            });
          }
        };
        VfsUtilCore.iterateChildrenRecursively(file, filter, new ContentIterator() {
          public boolean processFile(final VirtualFile fileOrDir) {
            final Change c = changeListManager.getChange(fileOrDir);
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

  private String getCheckinActionName(final VcsContext dataContext) {
    final Project project = dataContext.getProject();
    if (project == null) return VcsBundle.message("vcs.command.name.checkin");

    final AbstractVcs vcs = getCommonVcsFor(getRoots(dataContext), project);
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
  protected boolean approximatelyHasRoots(final VcsContext dataContext) {
    final FilePath[] paths = dataContext.getSelectedFilePaths();
    if (paths.length == 0) return false;
    final FileStatusManager fsm = FileStatusManager.getInstance(dataContext.getProject());
    for (final FilePath path : paths) {
      VirtualFile file = path.getVirtualFile();
      if (file == null) {
        continue;
      }
      FileStatus status = fsm.getStatus(file);
      if (isApplicableRoot(file, status, dataContext)) {
        return true;
      }
    }
    return false;
  }

  protected boolean isApplicableRoot(VirtualFile file, FileStatus status, VcsContext dataContext) {
    return status != FileStatus.UNKNOWN && status != FileStatus.IGNORED;
  }

  protected FilePath[] getRoots(final VcsContext context) {
    return context.getSelectedFilePaths();
  }

  protected boolean filterRootsBeforeAction() {
    return true;
  }
}
