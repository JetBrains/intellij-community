// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.HtmlPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.GO_TO_HASH;
import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.SHOW_HIDE_BRANCHES;

public class CommitPanel extends JBPanel {
  public static final int SIDE_BORDER = 14;
  private static final int INTERNAL_BORDER = 10;
  private static final int EXTERNAL_BORDER = 14;

  @NotNull private final VcsLogData myLogData;

  @NotNull private final ReferencesPanel myBranchesPanel;
  @NotNull private final ReferencesPanel myTagsPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final HashAndAuthorPanel myHashAndAuthorPanel;
  @NotNull private final BranchesPanel myContainingBranchesPanel;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final Consumer<CommitId> myNavigate;

  @Nullable private CommitId myCommit;
  @Nullable private CommitPresentationUtil.CommitPresentation myPresentation;

  public CommitPanel(@NotNull VcsLogData logData, @NotNull VcsLogColorManager colorManager, @NotNull Consumer<CommitId> navigate) {
    myLogData = logData;
    myColorManager = colorManager;
    myNavigate = navigate;

    setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    setOpaque(false);

    myMessagePanel = new MessagePanel();
    myHashAndAuthorPanel = new HashAndAuthorPanel();
    myBranchesPanel = new ReferencesPanel();
    myTagsPanel = new ReferencesPanel();
    myContainingBranchesPanel = new BranchesPanel();

    myMessagePanel.setBorder(JBUI.Borders.empty(EXTERNAL_BORDER, SIDE_BORDER, INTERNAL_BORDER, SIDE_BORDER));
    myHashAndAuthorPanel.setBorder(JBUI.Borders.empty(INTERNAL_BORDER, SIDE_BORDER));
    myBranchesPanel.setBorder(JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, 0, SIDE_BORDER));
    myTagsPanel.setBorder(JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, 0, SIDE_BORDER));
    myContainingBranchesPanel.setBorder(JBUI.Borders.empty(INTERNAL_BORDER, SIDE_BORDER, EXTERNAL_BORDER, SIDE_BORDER));

    add(myMessagePanel);
    add(myHashAndAuthorPanel);
    add(myBranchesPanel);
    add(myTagsPanel);
    add(myContainingBranchesPanel);
  }

  public void setCommit(@NotNull CommitId commit, @NotNull CommitPresentationUtil.CommitPresentation presentation) {
    if (!commit.equals(myCommit) || presentation.isResolved()) {
      myCommit = commit;
      myPresentation = presentation;

      myMessagePanel.update();
      myHashAndAuthorPanel.update();
    }

    List<String> branches = myLogData.getContainingBranchesGetter().requestContainingBranches(commit.getRoot(), commit.getHash());
    myContainingBranchesPanel.setBranches(branches);
  }

  public void setRefs(@NotNull Collection<VcsRef> refs) {
    List<VcsRef> references = sortRefs(refs);
    myBranchesPanel.setReferences(references.stream().filter(ref -> ref.getType().isBranch()).collect(Collectors.toList()));
    myTagsPanel.setReferences(references.stream().filter(ref -> !ref.getType().isBranch()).collect(Collectors.toList()));
  }

  public void update() {
    myMessagePanel.update();
    myHashAndAuthorPanel.update();
    myBranchesPanel.update();
    myTagsPanel.update();
    myContainingBranchesPanel.update();
  }

  public void updateBranches() {
    if (myCommit != null) {
      myContainingBranchesPanel
        .setBranches(myLogData.getContainingBranchesGetter().getContainingBranchesFromCache(myCommit.getRoot(), myCommit.getHash()));
    }
    else {
      myContainingBranchesPanel.setBranches(null);
    }
    myContainingBranchesPanel.update();
  }

  @NotNull
  private List<VcsRef> sortRefs(@NotNull Collection<VcsRef> refs) {
    VcsRef ref = ContainerUtil.getFirstItem(refs);
    if (ref == null) return ContainerUtil.emptyList();
    return ContainerUtil.sorted(refs, myLogData.getLogProvider(ref.getRoot()).getReferenceManager().getLabelsOrderComparator());
  }

  @Override
  public Color getBackground() {
    return getCommitDetailsBackground();
  }

  @NotNull
  public static Color getCommitDetailsBackground() {
    return UIUtil.getPanelBackground();
  }

  private class MessagePanel extends HtmlPanel {
    MessagePanel() {
      setOpaque(true);
    }

    @Override
    public void hyperlinkUpdate(@NotNull HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription().startsWith(GO_TO_HASH)) {
        CommitId commitId = notNull(myPresentation).parseTargetCommit(e);
        if (commitId != null) myNavigate.consume(commitId);
      }
      else {
        BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e);
      }
    }

    @NotNull
    @Override
    protected String getBody() {
      return myPresentation == null ? "" : myPresentation.getText();
    }

    @Override
    public Color getBackground() {
      if (UIUtil.isUnderDarcula()) {
        return getCommitDetailsBackground();
      }
      return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
    }

    @Override
    public void update() {
      setVisible(myPresentation != null); // looks weird when empty
      super.update();
    }
  }

  private class HashAndAuthorPanel extends HtmlPanel {
    @NotNull
    @Override
    protected String getBody() {
      return myPresentation == null ? "" : myPresentation.getHashAndAuthor();
    }

    @NotNull
    @Override
    protected Font getBodyFont() {
      return FontUtil.getCommitMetadataFont();
    }
  }

  private static class BranchesPanel extends HtmlPanel {
    @Nullable private List<String> myBranches;
    private boolean myExpanded = false;

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && SHOW_HIDE_BRANCHES.equals(e.getDescription())) {
        myExpanded = !myExpanded;
        update();
      }
    }

    void setBranches(@Nullable List<String> branches) {
      myBranches = branches;
      myExpanded = false;

      update();
    }

    @NotNull
    @Override
    protected String getBody() {
      return CommitPresentationUtil.getBranchesText(myBranches, myExpanded);
    }

    @Override
    public Color getBackground() {
      return getCommitDetailsBackground();
    }

    @NotNull
    @Override
    protected Font getBodyFont() {
      return FontUtil.getCommitMetadataFont();
    }
  }
}
