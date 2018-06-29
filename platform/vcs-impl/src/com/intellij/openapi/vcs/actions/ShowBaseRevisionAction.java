/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.diff.DiffMixin;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class ShowBaseRevisionAction extends AbstractVcsAction {
  @Override
  protected void actionPerformed(@NotNull VcsContext vcsContext) {
    Project project = assertNotNull(vcsContext.getProject());
    VirtualFile file = vcsContext.getSelectedFiles()[0];
    AbstractVcs vcs = assertNotNull(ChangesUtil.getVcsForFile(file, project));

    ProgressManager.getInstance().run(new MyTask(file, vcs, vcsContext));
  }

  private static class MyTask extends Task.Backgroundable {
    private final AbstractVcs vcs;
    private final VirtualFile selectedFile;
    private VcsRevisionDescription myDescription;
    private final VcsContext vcsContext;

    private MyTask(VirtualFile selectedFile, AbstractVcs vcs, VcsContext vcsContext) {
      super(vcsContext.getProject(), "Loading current revision", true);
      this.selectedFile = selectedFile;
      this.vcs = vcs;
      this.vcsContext = vcsContext;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myDescription = assertNotNull((DiffMixin)vcs.getDiffProvider()).getCurrentRevisionDescription(selectedFile);
    }

    @Override
    public void onSuccess() {
      if (myProject.isDisposed() || ! myProject.isOpen()) return;

      if (myDescription != null) {
        NotificationPanel panel = new NotificationPanel();
        panel.setText(createMessage(myDescription, selectedFile));
        final JBPopup message = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.getLabel()).createPopup();
        if (vcsContext.getEditor() != null) {
          message.showInBestPositionFor(vcsContext.getEditor());
        } else {
          message.showCenteredInCurrentWindow(vcsContext.getProject());
        }
      }
    }
  }

  private static String createMessage(VcsRevisionDescription description, final VirtualFile vf) {
    return "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" +
           VcsBundle.message("current.version.text", description.getAuthor(),
                             DateFormatUtil.formatPrettyDateTime(description.getRevisionDate()), description.getCommitMessage(),
                             description.getRevisionNumber().asString(), vf.getName()) + "</body></html>";
  }

  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    presentation.setEnabled(AbstractShowDiffAction.isEnabled(vcsContext, false));
  }

  static class NotificationPanel extends JPanel {
    protected final JEditorPane myLabel;

    public NotificationPanel() {
      super(new BorderLayout());

      myLabel = new JEditorPane(UIUtil.HTML_MIME, "");
      myLabel.setEditable(false);
      myLabel.setFont(UIUtil.getToolTipFont());

      setBorder(JBUI.Borders.empty(1, 15));

      add(myLabel, BorderLayout.CENTER);
      myLabel.setBackground(getBackground());
    }

    public void setText(String text) {
      myLabel.setText(text);
    }

    public JEditorPane getLabel() {
      return myLabel;
    }

    @Override
    public Color getBackground() {
      Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
      return color == null ? new Color(0xffffcc) : color;
    }
  }
}
