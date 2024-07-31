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
  @NotNull private final Project myProject;
  @NotNull private final StatusText myStatusText;
  @Nullable private List<? extends TreeNodeOnVcsRevision> mySelection;

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

  @NotNull
  @Override
  protected String getBody() {
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
