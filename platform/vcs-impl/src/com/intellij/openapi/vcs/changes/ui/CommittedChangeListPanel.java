// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowser;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CommittedChangeListPanel extends JPanel implements DataProvider {
  private final JLabel myDescriptionLabel;
  private final CommittedChangesBrowser myChangesBrowser;
  private final JEditorPane myCommitMessageArea;
  private final JPanel myCommitMessagePanel;
  private final Project myProject;

  private CommittedChangeList myChangeList;
  private Collection<Change> myChanges;

  public CommittedChangeListPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;

    myDescriptionLabel = new JLabel();
    myDescriptionLabel.setBorder(BorderFactory.createEtchedBorder());

    myChangesBrowser = new MyChangesBrowser(myProject);

    myCommitMessageArea = new JEditorPane(UIUtil.HTML_MIME, "");
    myCommitMessageArea.setBorder(JBUI.Borders.empty(3));
    myCommitMessageArea.setEditable(false);
    myCommitMessageArea.setBackground(UIUtil.getComboBoxDisabledBackground());
    myCommitMessageArea.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    myCommitMessagePanel = JBUI.Panels.simplePanel(ScrollPaneFactory.createScrollPane(myCommitMessageArea))
      .addToTop(SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myCommitMessageArea));

    Splitter splitter = new Splitter(true, 0.8f);
    splitter.setFirstComponent(myChangesBrowser);
    splitter.setSecondComponent(myCommitMessagePanel);

    add(splitter, BorderLayout.CENTER);
    add(myDescriptionLabel, BorderLayout.NORTH);

    setChangeList(createChangeList(Collections.emptyList()));
    setDescription(null);
  }

  public void setChangeList(@NotNull CommittedChangeList changeList) {
    myChangeList = changeList;
    myChanges = changeList.getChanges();

    myChangesBrowser.setChangesToDisplay(myChanges);

    myCommitMessageArea.setText(IssueLinkHtmlRenderer.formatTextIntoHtml(myProject, changeList.getComment().trim()));
    myCommitMessageArea.setCaretPosition(0);
  }

  public void setShowCommitMessage(boolean value) {
    myCommitMessagePanel.setVisible(value);
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
  public Object getData(@NotNull @NonNls final String dataId) {
    if (VcsDataKeys.CHANGES.is(dataId)) {
      return myChanges.toArray(new Change[0]);
    }
    if (VcsDataKeys.VCS.is(dataId)) {
      AbstractVcs vcs = myChangeList.getVcs();
      return vcs == null ? null : vcs.getKeyInstanceMethod();
    }
    if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      return new ChangeList[]{myChangeList};
    }
    return null;
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
