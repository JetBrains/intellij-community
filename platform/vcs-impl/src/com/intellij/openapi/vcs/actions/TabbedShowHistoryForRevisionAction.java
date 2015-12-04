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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.VcsRevisionNumberAware;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class TabbedShowHistoryForRevisionAction extends AbstractVcsAction {
  @Override
  protected void update(VcsContext context, Presentation presentation) {
    boolean visible = isVisible(context);
    presentation.setVisible(visible);
    if (visible) {
      boolean enabled = isEnabled(context);
      presentation.setEnabled(enabled);
    }
  }

  private static boolean isVisible(VcsContext context) {
    Project project = context.getProject();
    if (project == null) return false;
    ChangeList[] changeLists = context.getSelectedChangeLists();
    if (changeLists == null || changeLists.length != 1) return false;
    ChangeList changeList = changeLists[0];
    if (!(changeList instanceof CommittedChangeList) || !(changeList instanceof VcsRevisionNumberAware)) return false;
    AbstractVcs vcs = getVcsForChangeList(project, changeList);
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = vcs.getVcsHistoryProvider();
    if (!(vcsHistoryProvider instanceof VcsHistoryProviderEx)) return false;
    return true;
  }

  private static boolean isEnabled(VcsContext context) {
    Change[] changes = context.getSelectedChanges();
    if (changes == null || changes.length != 1) return false;
    Change change = changes[0];
    FilePath changedPath = getChangedPath(change);
    if (changedPath == null) return false;
    return true;
  }
  
  private static FilePath getChangedPath(Change change) {
    ContentRevision revision = change.getType() == Change.Type.DELETED ? change.getBeforeRevision() : change.getAfterRevision();
    return revision == null ? null : revision.getFile();
  }

  private static AbstractVcs getVcsForChangeList(Project project, ChangeList changeList) {
    Collection<Change> changes = changeList.getChanges();
    if (changes == null || changes.isEmpty()) return null;
    Change change = changes.iterator().next();
    if (change == null) return null;
    AbstractVcs vcs = getVcsForContentRevision(project, change.getAfterRevision());
    if (vcs != null) return vcs;
    return getVcsForContentRevision(project, change.getBeforeRevision());
  }

  private static AbstractVcs getVcsForContentRevision(Project project, ContentRevision contentRevision) {
    return ProjectLevelVcsManager.getInstance(project).getVcsFor(contentRevision.getFile());
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  protected void actionPerformed(@NotNull VcsContext context) {
    if (!isVisible(context) || !isEnabled(context)) return;
    
    Project project = context.getProject();
    CommittedChangeList changeList = (CommittedChangeList)context.getSelectedChangeLists()[0];
    AbstractVcs vcs = getVcsForChangeList(project, changeList);
    VcsHistoryProviderEx vcsHistoryProvider = (VcsHistoryProviderEx)vcs.getVcsHistoryProvider();
    AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    Change change = context.getSelectedChanges()[0];
    FilePath path = getChangedPath(change);
    VcsRevisionNumber revisionNumber = ((VcsRevisionNumberAware)changeList).getRevisionNumber();
    
    AbstractVcsHelper.getInstance(project).showFileHistory(vcsHistoryProvider, annotationProvider, path, null, vcs, revisionNumber);
  }

  @Override
  protected boolean forceSyncUpdate(AnActionEvent e) {
    return true;
  }
}
