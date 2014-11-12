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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

public class CommonCheckinFilesAction extends AbstractCommonCheckinAction {
  @Override
  protected String getActionName(@NotNull VcsContext dataContext) {
    final String checkinActionName = getCheckinActionName(dataContext);
    return modifyCheckinActionName(dataContext, checkinActionName);
  }

  private String modifyCheckinActionName(final VcsContext dataContext, String checkinActionName) {
    final FilePath[] roots = getRoots(dataContext);
    if (roots.length == 0) return checkinActionName;
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
    LocalChangeList defaultChangeList = changeListManager.getDefaultChangeList();

    final FilePath[] roots = getRoots(context);
    LocalChangeList changeList = null;
    for (final FilePath root : roots) {
      final VirtualFile file = root.getVirtualFile();
      if (file == null) continue;
      Collection<LocalChangeList> lists = getChangeListsForRoot(changeListManager, root);
      if (lists.contains(defaultChangeList)) {
        return defaultChangeList;
      }
      Iterator<LocalChangeList> it = lists.iterator();
      changeList = it.hasNext() ? it.next() : null;
    }
    return changeList == null ? defaultChangeList : changeList;
  }

  private static Collection<LocalChangeList> getChangeListsForRoot(final ChangeListManager changeListManager, final FilePath dirPath) {
    Collection<Change> changes = changeListManager.getChangesIn(dirPath);
    return ContainerUtil.map2Set(changes, new Function<Change, LocalChangeList>() {
      @Override
      public LocalChangeList fun(Change change) {
        return changeListManager.getChangeList(change);
      }
    });
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

  protected boolean isApplicableRoot(@NotNull VirtualFile file, @NotNull FileStatus status, @NotNull VcsContext dataContext) {
    return status != FileStatus.UNKNOWN && status != FileStatus.IGNORED;
  }

  @NotNull
  @Override
  protected FilePath[] getRoots(@NotNull final VcsContext context) {
    return context.getSelectedFilePaths();
  }

  @Override
  protected boolean filterRootsBeforeAction() {
    return true;
  }
}
