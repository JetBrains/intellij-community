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
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.VcsRevisionNumberAware;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.util.ObjectUtils.assertNotNull;


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
    CommittedChangeList changeList = (CommittedChangeList)event.getRequiredData(VcsDataKeys.CHANGE_LISTS)[0];
    AbstractVcs vcs = assertNotNull(getVcsForChangeList(project, changeList));
    VcsHistoryProviderEx vcsHistoryProvider = assertNotNull((VcsHistoryProviderEx)vcs.getVcsHistoryProvider());
    AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    Change change = event.getRequiredData(VcsDataKeys.CHANGES)[0];
    FilePath path = assertNotNull(getChangedPath(change));
    VcsRevisionNumber revisionNumber = ((VcsRevisionNumberAware)changeList).getRevisionNumber();

    AbstractVcsHelper.getInstance(project).showFileHistory(vcsHistoryProvider, annotationProvider, path, null, vcs, revisionNumber);
  }

  private static boolean isVisible(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) return false;
    ChangeList[] changeLists = event.getData(VcsDataKeys.CHANGE_LISTS);
    if (changeLists == null || changeLists.length != 1) return false;
    ChangeList changeList = changeLists[0];
    if (!(changeList instanceof CommittedChangeList) || !(changeList instanceof VcsRevisionNumberAware)) return false;
    AbstractVcs vcs = getVcsForChangeList(project, changeList);
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();
    if (!(vcsHistoryProvider instanceof VcsHistoryProviderEx)) return false;
    return true;
  }

  private static boolean isEnabled(@NotNull AnActionEvent event) {
    Change[] changes = event.getData(VcsDataKeys.CHANGES);
    if (changes == null || changes.length != 1) return false;
    Change change = changes[0];
    FilePath changedPath = getChangedPath(change);
    if (changedPath == null) return false;
    return true;
  }

  @Nullable
  private static FilePath getChangedPath(@NotNull Change change) {
    ContentRevision revision = change.getType() == Change.Type.DELETED ? change.getBeforeRevision() : change.getAfterRevision();
    return revision == null ? null : revision.getFile();
  }

  @Nullable
  private static AbstractVcs getVcsForChangeList(@NotNull Project project, @NotNull ChangeList changeList) {
    Collection<Change> changes = changeList.getChanges();
    if (changes == null || changes.isEmpty()) return null;
    Change change = ContainerUtil.getFirstItem(changes);
    if (change == null) return null;
    return ChangesUtil.getVcsForChange(change, project);
  }
}
