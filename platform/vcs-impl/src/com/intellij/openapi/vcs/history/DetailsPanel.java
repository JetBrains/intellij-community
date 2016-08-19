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
package com.intellij.openapi.vcs.history;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.HtmlPanel;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;
import static com.intellij.openapi.vcs.ui.FontUtil.getHtmlWithFonts;

class DetailsPanel extends HtmlPanel implements DataProvider, CopyProvider {
  @NotNull private final Project myProject;
  @NotNull private final StatusText myStatusText;
  @NotNull private String myText = "";

  public DetailsPanel(@NotNull Project project) {
    myProject = project;
    myStatusText = new StatusText() {
      @Override
      protected boolean isStatusVisible() {
        return StringUtil.isEmpty(myText);
      }
    };
    myStatusText.setText("Commit message");
    myStatusText.attachTo(this);

    setPreferredSize(new Dimension(150, 100));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myStatusText.paint(this, g);
  }

  public void update(@NotNull List<TreeNodeOnVcsRevision> selection) {
    if (selection.isEmpty()) {
      setText("");
      return;
    }
    boolean addRevisionInfo = selection.size() > 1;
    StringBuilder html = new StringBuilder();
    for (TreeNodeOnVcsRevision revision : selection) {
      String message = revision.getCommitMessage();
      if (StringUtil.isEmpty(message)) continue;
      if (html.length() > 0) {
        html.append("<br/><br/>");
      }
      if (addRevisionInfo) {
        String revisionInfo = FileHistoryPanelImpl.getPresentableText(revision.getRevision(), false);
        html.append("<font color=\"#").append(Integer.toHexString(JBColor.gray.getRGB()).substring(2)).append("\">")
          .append(getHtmlWithFonts(revisionInfo)).append("</font><br/>");
      }
      html.append(getHtmlWithFonts(formatTextWithLinks(myProject, message)));
    }
    myText = html.toString();
    if (myText.isEmpty()) {
      setText("");
    }
    else {
      setText("<html><head>" +
              UIUtil.getCssFontDeclaration(VcsHistoryUtil.getCommitDetailsFont()) +
              "</head><body>" +
              myText +
              "</body></html>");
      setCaretPosition(0);
    }
  }

  @Override
  public Color getBackground() {
    return UIUtil.getEditorPaneBackground();
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

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }
}
