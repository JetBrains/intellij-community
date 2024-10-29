// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CommittedChangeListPanel extends JPanel implements UiDataProvider {
  private final Project myProject;

  private final JLabel myDescriptionLabel;
  private final CommittedChangesBrowser myChangesBrowser;
  private final JEditorPane myCommitMessageArea;
  private final JScrollPane myCommitMessageScrollPane;

  private @NotNull CommittedChangeList myChangeList;
  private @NotNull Collection<Change> myChanges;

  private boolean myShowSideBorders = true; // borders look better in dialogs
  private boolean myShowCommitMessage = true;

  public CommittedChangeListPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;

    myChangeList = createChangeList(Collections.emptyList());
    myChanges = myChangeList.getChanges();

    myDescriptionLabel = new JLabel();
    myDescriptionLabel.setBorder(BorderFactory.createEtchedBorder());

    myChangesBrowser = new MyChangesBrowser(myProject);

    myCommitMessageArea = new JEditorPane(UIUtil.HTML_MIME, "") {
      @Override
      public void updateUI() {
        super.updateUI();
        setText(getChangelistCommentHtml());
      }
    };
    myCommitMessageArea.setBorder(JBUI.Borders.empty(3));
    myCommitMessageArea.setEditable(false);
    myCommitMessageArea.setBackground(UIUtil.getTreeBackground());
    myCommitMessageArea.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    myCommitMessageScrollPane = ScrollPaneFactory.createScrollPane(myCommitMessageArea);

    Splitter splitter = new OnePixelSplitter(true, 0.8f);
    splitter.setFirstComponent(myChangesBrowser);
    splitter.setSecondComponent(myCommitMessageScrollPane);

    add(splitter, BorderLayout.CENTER);
    add(myDescriptionLabel, BorderLayout.NORTH);
    updatePresentation();

    setDescription(null);
  }

  private void updatePresentation() {
    myCommitMessageScrollPane.setVisible(myShowCommitMessage);

    if (myShowSideBorders) {
      if (myShowCommitMessage) {
        myChangesBrowser.setViewerBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT | SideBorder.RIGHT));
        myCommitMessageScrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.RIGHT | SideBorder.BOTTOM));
      }
      else {
        myChangesBrowser.setViewerBorder(IdeBorderFactory.createBorder(SideBorder.ALL));
      }
    }
    else {
      myChangesBrowser.setViewerBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
      myCommitMessageScrollPane.setBorder(JBUI.Borders.empty());
    }
  }

  public void setChangeList(@NotNull CommittedChangeList changeList) {
    myChangeList = changeList;
    myChanges = changeList.getChanges();

    myChangesBrowser.setChangesToDisplay(myChanges);

    myCommitMessageArea.setText(getChangelistCommentHtml());
    myCommitMessageArea.setCaretPosition(0);
  }

  @NotNull
  private @Nls String getChangelistCommentHtml() {
    return IssueLinkHtmlRenderer.formatTextIntoHtml(myProject, myChangeList.getComment().trim());
  }

  public void setShowCommitMessage(boolean value) {
    myShowCommitMessage = value;
    updatePresentation();
  }

  public void setShowSideBorders(boolean value) {
    myShowSideBorders = value;
    updatePresentation();
  }

  public void setDescription(@Nullable @NlsContexts.Label String description) {
    myDescriptionLabel.setText(XmlStringUtil.wrapInHtml(StringUtil.notNullize(description)));
    myDescriptionLabel.setVisible(description != null);
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myChangesBrowser.getPreferredFocusedComponent();
  }

  @NotNull
  public CommittedChangesBrowser getChangesBrowser() {
    return myChangesBrowser;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    AbstractVcs vcs = myChangeList.getVcs();
    sink.set(VcsDataKeys.CHANGES, myChanges.toArray(Change.EMPTY_CHANGE_ARRAY));
    sink.set(VcsDataKeys.VCS, vcs == null ? null : vcs.getKeyInstanceMethod());
    sink.set(VcsDataKeys.CHANGE_LISTS, new ChangeList[]{myChangeList});
  }

  @NotNull
  public static CommittedChangeListImpl createChangeList(@NotNull Collection<Change> changes) {
    return new CommittedChangeListImpl("", "", "", -1, new Date(0), changes);
  }

  private static class MyChangesBrowser extends CommittedChangesBrowser {
    MyChangesBrowser(@NotNull Project project) {
      super(project);
    }

    @NotNull
    @Override
    protected List<AnAction> createPopupMenuActions() {
      return ContainerUtil.append(
        super.createPopupMenuActions(),
        ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER)
      );
    }
  }
}
