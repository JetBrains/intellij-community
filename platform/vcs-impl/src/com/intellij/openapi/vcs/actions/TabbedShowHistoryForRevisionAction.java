/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ObjectUtils.tryCast;


public class TabbedShowHistoryForRevisionAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    boolean visible = isVisible(e);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(visible && isEnabled(e));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    AbstractVcs vcs = assertNotNull(getVcs(project, event.getData(VcsDataKeys.VCS)));
    VcsHistoryProviderEx vcsHistoryProvider = assertNotNull((VcsHistoryProviderEx)vcs.getVcsHistoryProvider());

    Change change = event.getRequiredData(VcsDataKeys.SELECTED_CHANGES)[0];
    ContentRevision revision = assertNotNull(getContentRevision(change));

    AbstractVcsHelperImpl helper = assertNotNull(getVcsHelper(project));
    helper.showFileHistory(vcsHistoryProvider, revision.getFile(), vcs, revision.getRevisionNumber());
  }

  private static boolean isVisible(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) return false;
    if (getVcsHelper(project) == null) return false;
    AbstractVcs vcs = getVcs(project, event.getData(VcsDataKeys.VCS));
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();
    if (!(vcsHistoryProvider instanceof VcsHistoryProviderEx)) return false;
    return true;
  }

  private static boolean isEnabled(@NotNull AnActionEvent event) {
    Change[] changes = event.getData(VcsDataKeys.SELECTED_CHANGES);
    if (changes == null || changes.length != 1) return false;
    return getContentRevision(changes[0]) != null;
  }

  @Nullable
  private static ContentRevision getContentRevision(@NotNull Change change) {
    return change.getType() == Change.Type.DELETED ? change.getBeforeRevision() : change.getAfterRevision();
  }

  @Nullable
  private static AbstractVcs getVcs(@NotNull Project project, @Nullable VcsKey vcsKey) {
    return vcsKey == null ? null : ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsKey.getName());
  }

  @Nullable
  private static AbstractVcsHelperImpl getVcsHelper(@NotNull Project project) {
    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
    return tryCast(helper, AbstractVcsHelperImpl.class);
  }
}
