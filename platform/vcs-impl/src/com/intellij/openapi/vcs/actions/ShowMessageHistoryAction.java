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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.actions.ContentChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.ui.Refreshable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Action showing the history of recently used commit messages. Source code of this class is provided
 * as a sample of using the {@link CheckinProjectPanel} API. Actions to be shown in the commit dialog
 * should be added to the {@code Vcs.MessageActionGroup} action group.
 *
 * @author lesya
 * @since 5.1
 */
public class ShowMessageHistoryAction extends AnAction implements DumbAware {
  public ShowMessageHistoryAction() {
    setEnabledInModalContext(true);
  }

  public void update(AnActionEvent e) {
    super.update(e);

    final DataContext dc = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    Object panel = CheckinProjectPanel.PANEL_KEY.getData(dc);
    if (! (panel instanceof CommitMessageI)) {
      panel = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dc);
    }

    if (project == null || panel == null) {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setVisible(true);
      final ArrayList<String> recentMessages = VcsConfiguration.getInstance(project).getRecentMessages();
      e.getPresentation().setEnabled(!recentMessages.isEmpty());
    }
  }

  public void actionPerformed(AnActionEvent e) {
    CommitMessageI commitMessageI;
    final DataContext dc = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    final Refreshable panel = CheckinProjectPanel.PANEL_KEY.getData(dc);
    commitMessageI = (panel instanceof CommitMessageI) ? (CommitMessageI)panel : VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dc);

    if (commitMessageI != null && project != null) {
      final VcsConfiguration configuration = VcsConfiguration.getInstance(project);


      if (!configuration.getRecentMessages().isEmpty()) {

        final ContentChooser<String> contentChooser =
          new ContentChooser<String>(project, VcsBundle.message("dialog.title.choose.commit.message.from.history"), false) {
            protected void removeContentAt(final String content) {
              configuration.removeMessage(content);
            }

            protected String getStringRepresentationFor(final String content) {
              return content;
            }

            protected List<String> getContents() {
              final List<String> recentMessages = configuration.getRecentMessages();
              Collections.reverse(recentMessages);
              return recentMessages;
            }
          };

        if (contentChooser.showAndGet()) {
          final int selectedIndex = contentChooser.getSelectedIndex();

          if (selectedIndex >= 0) {
            commitMessageI.setCommitMessage(contentChooser.getAllContents().get(selectedIndex));
          }
        }
      }
    }
  }
}
