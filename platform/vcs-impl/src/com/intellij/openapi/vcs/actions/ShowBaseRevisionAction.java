// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
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
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static java.util.Objects.requireNonNull;

public class ShowBaseRevisionAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = requireNonNull(e.getProject());
    VirtualFile file = requireNonNull(VcsContextUtil.selectedFile(e.getDataContext()));
    AbstractVcs vcs = requireNonNull(ChangesUtil.getVcsForFile(file, project));
    Editor editor = e.getData(CommonDataKeys.EDITOR);

    ProgressManager.getInstance().run(new MyTask(project, file, vcs, editor));
  }

  private static final class MyTask extends Task.Backgroundable {
    private final AbstractVcs vcs;
    private final VirtualFile selectedFile;
    private VcsRevisionDescription myDescription;
    private final Editor editor;

    private MyTask(Project project, VirtualFile selectedFile, AbstractVcs vcs, Editor editor) {
      super(project, VcsBundle.message("progress.title.loading.current.revision"), true);
      this.selectedFile = selectedFile;
      this.vcs = vcs;
      this.editor = editor;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myDescription = requireNonNull((DiffMixin)vcs.getDiffProvider()).getCurrentRevisionDescription(selectedFile);
    }

    @Override
    public void onSuccess() {
      if (myProject.isDisposed() || !myProject.isOpen()) return;

      if (myDescription != null) {
        NotificationPanel panel = new NotificationPanel();
        panel.setText(createMessage(myProject, myDescription, selectedFile));
        final JBPopup message = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.getLabel()).createPopup();
        if (editor != null && editor.getComponent().isShowing()) {
          message.showInBestPositionFor(editor);
        }
        else {
          message.showCenteredInCurrentWindow(myProject);
        }
      }
    }
  }

  @Nls
  private static String createMessage(@NotNull Project project, @NotNull VcsRevisionDescription description, @NotNull VirtualFile vf) {
    String commitMessage = IssueLinkHtmlRenderer.formatTextWithLinks(project, StringUtil.notNullize(description.getCommitMessage()));
    String message = VcsBundle.message("current.version.text",
                                       description.getAuthor(),
                                       DateFormatUtil.formatPrettyDateTime(description.getRevisionDate()),
                                       commitMessage,
                                       description.getRevisionNumber().asString(),
                                       vf.getName());
    return "<html><head>" + UIUtil.getCssFontDeclaration(StartupUiUtil.getLabelFont()) + "</head><body>" + message + "</body></html>"; //NON-NLS
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isEnabled = AbstractShowDiffAction.isEnabled(e.getDataContext(), false);
    e.getPresentation().setEnabled(isEnabled);
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

    public void setText(@Nls String text) {
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
