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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.containers.UtilKt.getIfSingle;

public class CommonCheckinFilesAction extends AbstractCommonCheckinAction {
  @Override
  protected String getActionName(@NotNull VcsContext dataContext) {
    String actionName = Optional.ofNullable(dataContext.getProject())
      .map(project -> getCommonVcs(getRootsStream(dataContext), project))
      .map(AbstractVcs::getCheckinEnvironment)
      .map(CheckinEnvironment::getCheckinOperationName)
      .orElse(VcsBundle.message("vcs.command.name.checkin"));

    return modifyCheckinActionName(dataContext, actionName);
  }

  private String modifyCheckinActionName(@NotNull VcsContext dataContext, String checkinActionName) {
    String result = checkinActionName;
    List<FilePath> roots = getRootsStream(dataContext).limit(2).collect(Collectors.toList());

    if (!roots.isEmpty()) {
      String messageKey = roots.get(0).isDirectory() ? "action.name.checkin.directory" : "action.name.checkin.file";
      result = VcsBundle.message(StringUtil.pluralize(messageKey, roots.size()), checkinActionName);
    }

    return result;
  }

  @Override
  protected String getMnemonicsFreeActionName(@NotNull VcsContext context) {
    return modifyCheckinActionName(context, VcsBundle.message("vcs.command.name.checkin.no.mnemonics"));
  }

  @Nullable
  @Override
  protected LocalChangeList getInitiallySelectedChangeList(@NotNull VcsContext context, @NotNull Project project) {
    ChangeListManager manager = ChangeListManager.getInstance(project);
    LocalChangeList defaultChangeList = manager.getDefaultChangeList();
    LocalChangeList result = null;

    for (FilePath root : getRoots(context)) {
      if (root.getVirtualFile() == null) continue;

      Collection<Change> changes = manager.getChangesIn(root);
      if (defaultChangeList != null && containsAnyChange(defaultChangeList, changes)) {
        return defaultChangeList;
      }
      result = changes.stream().findFirst().map(manager::getChangeList).orElse(null);
    }

    return ObjectUtils.chooseNotNull(result, defaultChangeList);
  }

  @Override
  protected boolean approximatelyHasRoots(@NotNull VcsContext dataContext) {
    ChangeListManager manager = ChangeListManager.getInstance(dataContext.getProject());

    return getRootsStream(dataContext)
      .map(FilePath::getVirtualFile)
      .filter(Objects::nonNull)
      .anyMatch(file -> isApplicableRoot(file, manager.getStatus(file), dataContext));
  }

  protected boolean isApplicableRoot(@NotNull VirtualFile file, @NotNull FileStatus status, @NotNull VcsContext dataContext) {
    return status != FileStatus.UNKNOWN && status != FileStatus.IGNORED;
  }

  @NotNull
  @Override
  protected FilePath[] getRoots(@NotNull VcsContext context) {
    return context.getSelectedFilePaths();
  }

  @NotNull
  protected Stream<FilePath> getRootsStream(@NotNull VcsContext context) {
    return context.getSelectedFilePathsStream();
  }

  private static boolean containsAnyChange(@NotNull LocalChangeList changeList, @NotNull Collection<Change> changes) {
    return changes.stream().anyMatch(changeList.getChanges()::contains);
  }

  @Nullable
  private static AbstractVcs getCommonVcs(@NotNull Stream<FilePath> roots, @NotNull Project project) {
    return getIfSingle(
      roots.map(root -> VcsUtil.getVcsFor(project, root))
        .filter(Objects::nonNull)
        .distinct()
        .limit(Math.min(2, ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length))
    );
  }
}
