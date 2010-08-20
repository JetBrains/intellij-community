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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProviderBackgroundableProxy;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.impl.CachedRevisionsContents;
import com.intellij.openapi.vcs.history.impl.VcsHistoryDialog;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsSelectionUtil;

import java.util.List;

public class SelectedBlockHistoryAction extends AbstractVcsAction {

  protected boolean isEnabled(VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    if (selectedFiles == null) return false;
    if (selectedFiles.length == 0) return false;
    VirtualFile file = selectedFiles[0];
    Project project = context.getProject();
    if (project == null) return false;
    final ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(project);
    final BackgroundableActionEnabledHandler handler =
      vcsManager.getBackgroundableActionHandler(VcsBackgroundableActions.HISTORY_FOR_SELECTION);
    if (handler.isInProgress(VcsBackgroundableActions.keyFrom(file))) return false;
    AbstractVcs vcs = vcsManager.getVcsFor(file);
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = vcs.getVcsBlockHistoryProvider();
    if (vcsHistoryProvider == null) return false;
    if (! AbstractVcs.fileInVcsByFileStatus(project, new FilePathImpl(file))) return false;

    VcsSelection selection = VcsSelectionUtil.getSelection(context);
    if (selection == null) {
      return false;
    }
    return true;
  }

  public void actionPerformed(final VcsContext context) {
    try {
      final VcsSelection selection = VcsSelectionUtil.getSelection(context);
      VirtualFile file = FileDocumentManager.getInstance().getFile(selection.getDocument());
      final Project project = context.getProject();
      if (project == null) return;
      final AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
      if (activeVcs == null) return;

      final VcsHistoryProvider provider = activeVcs.getVcsBlockHistoryProvider();

      final int selectionStart = selection.getSelectionStartLineNumber();
      final int selectionEnd = selection.getSelectionEndLineNumber();

      final CachedRevisionsContents cachedRevisionsContents = new CachedRevisionsContents(project, file);
      new VcsHistoryProviderBackgroundableProxy(project, provider).createSessionFor(new FilePathImpl(file),
        new Consumer<VcsHistorySession>() {
          public void consume(VcsHistorySession session) {
            if (session == null) return;
            final VcsHistoryDialog vcsHistoryDialog =
              new VcsHistoryDialog(project,
                                        context.getSelectedFiles()[0],
                                        provider,
                                        session,
                                        activeVcs,
                                        Math.min(selectionStart, selectionEnd),
                                        Math.max(selectionStart, selectionEnd),
                                        selection.getDialogTitle(), cachedRevisionsContents);

            vcsHistoryDialog.show();
          }
        }, VcsBackgroundableActions.HISTORY_FOR_SELECTION, false, new Consumer<VcsHistorySession>() {
          @Override
          public void consume(VcsHistorySession vcsHistorySession) {
            if (vcsHistorySession == null) return;
            final List<VcsFileRevision> revisionList = vcsHistorySession.getRevisionList();
            cachedRevisionsContents.setRevisions(revisionList);
            if (VcsConfiguration.getInstance(project).SHOW_ONLY_CHANGED_IN_SELECTION_DIFF) {
              // preload while in bckgrnd
              cachedRevisionsContents.loadContentsFor(revisionList.toArray(new VcsFileRevision[revisionList.size()]));
            }
          }
        });
    }
    catch (Exception exception) {
      reportError(exception);
    }

  }

  protected void update(VcsContext context, Presentation presentation) {
    presentation.setEnabled(isEnabled(context));
    VcsSelection selection = VcsSelectionUtil.getSelection(context);
    if (selection != null) {
      presentation.setText(selection.getActionName());
    }
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected static void reportError(Exception exception) {
    Messages.showMessageDialog(exception.getLocalizedMessage(), VcsBundle.message("message.title.could.not.load.file.history"), Messages.getErrorIcon());
  }
}
