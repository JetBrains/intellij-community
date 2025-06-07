// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.HtmlPanel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;
import static com.intellij.openapi.vcs.ui.FontUtil.getHtmlWithFonts;

class DetailsPanel extends HtmlPanel implements UiDataProvider, CopyProvider {
  private final @NotNull Project myProject;
  private final @NotNull StatusText myStatusText;
  private @Nullable List<? extends TreeNodeOnVcsRevision> mySelection;

  DetailsPanel(@NotNull Project project) {
    myProject = project;
    myStatusText = new StatusText() {
      @Override
      protected boolean isStatusVisible() {
        return mySelection == null || mySelection.isEmpty();
      }
    };
    myStatusText.setText(VcsBundle.message("file.history.details.empty.status"));
    myStatusText.attachTo(this);

    setPreferredSize(new JBDimension(150, 100));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myStatusText.paint(this, g);
  }

  public void update(@NotNull List<? extends TreeNodeOnVcsRevision> selection) {
    mySelection = selection;
    update();
  }

  @Override
  protected @NotNull String getBody() {
    if (mySelection == null || mySelection.isEmpty()) {
      return "";
    }

    boolean addRevisionInfo = mySelection.size() > 1;
    @Nls StringBuilder html = new StringBuilder();
    for (TreeNodeOnVcsRevision revision : mySelection) {
      String message = revision.getRevision().getCommitMessage();
      if (StringUtil.isEmpty(message)) continue;
      if (!html.isEmpty()) {
        html.append("<br/><br/>"); //NON-NLS
      }
      if (addRevisionInfo) {
        String revisionInfo = FileHistoryPanelImpl.getPresentableText(revision.getRevision(), false);
        html.append("<font color=\"").append(ColorUtil.toHtmlColor(JBColor.gray).substring(2)).append("\">") //NON-NLS
            .append(getHtmlWithFonts(revisionInfo)).append("</font><br/>"); //NON-NLS
      }
      html.append(getHtmlWithFonts(formatTextWithLinks(myProject, message)));
    }
    return html.toString();
  }

  @Override
  public Color getBackground() {
    return UIUtil.getEditorPaneBackground();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    String selectedText = getSelectedText();
    if (selectedText == null || selectedText.isEmpty()) selectedText = StringUtil.removeHtmlTags(getText());
    CopyPasteManager.getInstance().setContents(new StringSelection(selectedText));
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.COPY_PROVIDER, this);
  }
}
