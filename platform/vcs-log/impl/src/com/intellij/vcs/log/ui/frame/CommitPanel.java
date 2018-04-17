// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.HtmlPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.ContainingBranchesGetter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.EDITOR_PROP;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.GO_TO_HASH;
import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.SHOW_HIDE_BRANCHES;

public class CommitPanel extends JBPanel {
  public static final int SIDE_BORDER = 14;
  private static final int INTERNAL_BORDER = 10;
  private static final int EXTERNAL_BORDER = 14;
  private static final int ROOT_ICON_SIZE = 13;
  private static final int ROOT_GAP = 4;

  @NotNull private final VcsLogData myLogData;

  @NotNull private final ReferencesPanel myBranchesPanel;
  @NotNull private final ReferencesPanel myTagsPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final HashAndAuthorPanel myHashAndAuthorPanel;
  @NotNull private final RootPanel myRootPanel;
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
    JBPanel metadataPanel = new JBPanel(new BorderLayout(0, 0));
    myHashAndAuthorPanel = new HashAndAuthorPanel();
    myRootPanel = new RootPanel(myHashAndAuthorPanel);
    myBranchesPanel = new ReferencesPanel();
    myTagsPanel = new ReferencesPanel();
    myContainingBranchesPanel = new BranchesPanel();

    metadataPanel.setOpaque(false);

    myMessagePanel.setBorder(JBUI.Borders.empty(EXTERNAL_BORDER, SIDE_BORDER, INTERNAL_BORDER, SIDE_BORDER));
    myHashAndAuthorPanel.setBorder(JBUI.Borders.empty());
    metadataPanel.setBorder(JBUI.Borders.empty(INTERNAL_BORDER, SIDE_BORDER));
    myContainingBranchesPanel.setBorder(JBUI.Borders.empty(0, SIDE_BORDER, EXTERNAL_BORDER, SIDE_BORDER));

    add(myMessagePanel);
    metadataPanel.add(myRootPanel, BorderLayout.WEST);
    metadataPanel.add(myHashAndAuthorPanel, BorderLayout.CENTER);
    add(metadataPanel);
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

      myRootPanel.setRoot(commit.getRoot());
    }

    List<String> branches = myLogData.getContainingBranchesGetter().requestContainingBranches(commit.getRoot(), commit.getHash());
    myContainingBranchesPanel.setBranches(branches);
  }

  public void setRefs(@NotNull Collection<VcsRef> refs) {
    List<VcsRef> references = sortRefs(refs);
    myBranchesPanel.setReferences(references.stream().filter(ref -> ref.getType().isBranch()).collect(Collectors.toList()));
    myTagsPanel.setReferences(references.stream().filter(ref -> !ref.getType().isBranch()).collect(Collectors.toList()));
    if (myTagsPanel.isVisible()) {
      myBranchesPanel.setBorder(JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, 0, SIDE_BORDER));
      myTagsPanel.setBorder(JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, INTERNAL_BORDER, SIDE_BORDER));
    }
    else if (myBranchesPanel.isVisible()) {
      myBranchesPanel.setBorder(JBUI.Borders.empty(0, SIDE_BORDER - ReferencesPanel.H_GAP, INTERNAL_BORDER, SIDE_BORDER));
    }
  }

  public void update() {
    myMessagePanel.update();
    myHashAndAuthorPanel.update();
    myRootPanel.update();
    myBranchesPanel.update();
    myTagsPanel.update();
    myContainingBranchesPanel.update();
  }

  public void updateBranches() {
    if (myCommit != null) {
      ContainingBranchesGetter getter = myLogData.getContainingBranchesGetter();
      myContainingBranchesPanel.setBranches(getter.getContainingBranchesFromCache(myCommit.getRoot(), myCommit.getHash()));
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
      if (hasPanelBackground()) {
        return getCommitDetailsBackground();
      }
      return UIUtil.getTreeBackground();
    }

    @Override
    public void update() {
      setVisible(myPresentation != null); // looks weird when empty
      setOpaque(!hasPanelBackground());
      super.update();
    }

    protected boolean hasPanelBackground() {
      return UIUtil.isUnderDarcula() || hasBackgroundImage();
    }

    private boolean hasBackgroundImage() {
      return !StringUtil.isEmpty(PropertiesComponent.getInstance().getValue(EDITOR_PROP)) ||
             !StringUtil.isEmpty(PropertiesComponent.getInstance(myLogData.getProject()).getValue(EDITOR_PROP));
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

    @Override
    public void update() {
      setVisible(myPresentation != null);
      super.update();
    }
  }

  private static class BranchesPanel extends HtmlPanel {
    @Nullable private List<String> myBranches;
    private boolean myExpanded = false;

    @Override
    public void reshape(int x, int y, int w, int h) {
      int oldWidth = getWidth();
      super.reshape(x, y, w, h);
      if (w != oldWidth) update();
    }

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
      Insets insets = getInsets();
      String text = CommitPresentationUtil.getBranchesText(myBranches, myExpanded, getWidth() - insets.left - insets.right,
                                                           getFontMetrics(getBodyFont()));
      if (myExpanded) return text;
      return "<nobr>" + text + "</nobr>";
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

  private class RootPanel extends Wrapper {
    private final JComponent myReferent;
    @Nullable private ColorIcon myIcon;
    @Nullable private String myTooltipText;

    public RootPanel(@NotNull JComponent component) {
      myReferent = component;
      setVerticalSizeReferent(myReferent);
      addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          if (IdeTooltipManager.getInstance().hasCurrent()) {
            IdeTooltipManager.getInstance().hideCurrent(e);
            return;
          }
          if (myIcon == null || myTooltipText == null) return;
          VcsLogUiUtil.showTooltip(RootPanel.this, new Point(myIcon.getIconWidth() / 2, 0), Balloon.Position.above, myTooltipText);
        }
      });
    }

    @Override
    public Dimension getPreferredSize() {
      if (myIcon == null) return new Dimension(0, 0);
      Dimension size = super.getPreferredSize();
      return new Dimension(myIcon.getIconWidth() + JBUI.scale(ROOT_GAP), size.height);
    }

    public void setRoot(@NotNull VirtualFile root) {
      if (myColorManager.isMultipleRoots()) {
        JBColor color = VcsLogGraphTable.getRootBackgroundColor(root, myColorManager);
        myIcon = JBUI.scale(new ColorIcon(ROOT_ICON_SIZE, color));
        myTooltipText = root.getPath();
      }
      else {
        myIcon = null;
        myTooltipText = null;
      }
    }

    public void update() {
      setVisible(myIcon != null);
      revalidate();
      repaint();
    }

    @Override
    public Color getBackground() {
      return getCommitDetailsBackground();
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (myIcon != null) {
        int h = FontUtil.getStandardAscent(myHashAndAuthorPanel.getBodyFont(), g);
        FontMetrics metrics = getFontMetrics(myHashAndAuthorPanel.getBodyFont());
        myIcon.paintIcon(this, g, 0, metrics.getMaxAscent() - h + (h - myIcon.getIconHeight() - 1) / 2);
      }
    }
  }
}
