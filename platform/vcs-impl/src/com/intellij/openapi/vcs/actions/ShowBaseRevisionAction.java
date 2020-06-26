// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.diff.DiffMixin;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class ShowBaseRevisionAction extends AbstractVcsAction {
  @Override
  protected void actionPerformed(@NotNull VcsContext vcsContext) {
    Project project = Objects.requireNonNull(vcsContext.getProject());
    VirtualFile file = vcsContext.getSelectedFiles()[0];
    AbstractVcs vcs = Objects.requireNonNull(ChangesUtil.getVcsForFile(file, project));

    ProgressManager.getInstance().run(new MyTask(file, vcs, vcsContext));
  }

  private static final class MyTask extends Task.Backgroundable {
    private final AbstractVcs vcs;
    private final VirtualFile selectedFile;
    private VcsRevisionDescription myDescription;
    private final VcsContext vcsContext;

    private MyTask(VirtualFile selectedFile, AbstractVcs vcs, VcsContext vcsContext) {
      super(vcsContext.getProject(), VcsBundle.message("progress.title.loading.current.revision"), true);
      this.selectedFile = selectedFile;
      this.vcs = vcs;
      this.vcsContext = vcsContext;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myDescription = Objects.requireNonNull((DiffMixin)vcs.getDiffProvider()).getCurrentRevisionDescription(selectedFile);
    }

    @Override
    public void onSuccess() {
      if (myProject.isDisposed() || !myProject.isOpen()) return;

      if (myDescription != null) {
        NotificationPanel panel = new NotificationPanel();
        panel.setText(createMessage(myProject, myDescription, selectedFile));
        final JBPopup message = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.getLabel()).createPopup();
        if (vcsContext.getEditor() != null) {
          message.showInBestPositionFor(vcsContext.getEditor());
        }
        else {
          message.showCenteredInCurrentWindow(myProject);
        }
      }
    }
  }

  private static String createMessage(@NotNull Project project, @NotNull VcsRevisionDescription description, @NotNull VirtualFile vf) {
    String commitMessage = IssueLinkHtmlRenderer.formatTextWithLinks(project, StringUtil.notNullize(description.getCommitMessage()));
    String message = VcsBundle.message("current.version.text",
                                       description.getAuthor(),
                                       DateFormatUtil.formatPrettyDateTime(description.getRevisionDate()),
                                       commitMessage,
                                       description.getRevisionNumber().asString(),
                                       vf.getName());
    return "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" + message + "</body></html>";
  }

  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    presentation.setEnabled(AbstractShowDiffAction.isEnabled(vcsContext, false));
  }

  static class NotificationPanel extends JPanel {
    protected final JEditorPane myLabel;

    NotificationPanel() {
      super(new BorderLayout());

      myLabel = new JEditorPane(UIUtil.HTML_MIME, "");
      myLabel.setEditable(false);
      myLabel.setFont(UIUtil.getToolTipFont());
      myLabel.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

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
