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
package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Kirill Likhodedov
 */
class DetailsPanel extends JPanel implements EditorColorsListener {
  private static final int MAX_ROWS = 50;

  @NotNull private final VcsLogData myLogData;

  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JPanel myMainContentPanel;
  @NotNull private final StatusText myEmptyText;

  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private VisiblePack myDataPack;
  @NotNull private List<Integer> mySelection = ContainerUtil.emptyList();
  @NotNull private Set<VcsFullCommitDetails> myCommitDetails = Collections.emptySet();

  DetailsPanel(@NotNull VcsLogData logData,
               @NotNull VcsLogColorManager colorManager,
               @NotNull VisiblePack initialDataPack,
               @NotNull Disposable parent) {
    myLogData = logData;
    myColorManager = colorManager;
    myDataPack = initialDataPack;

    myScrollPane = new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(JBUI.scale(10));
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(JBUI.scale(10));
    myMainContentPanel = new ScrollablePanel() {
      @Override
      public boolean getScrollableTracksViewportWidth() {
        boolean expanded = false;
        for (Component c : getComponents()) {
          if (c instanceof CommitPanel && ((CommitPanel)c).isExpanded()) {
            expanded = true;
            break;
          }
        }
        return !expanded;
      }

      @Override
      public Dimension getPreferredSize() {
        Dimension preferredSize = super.getPreferredSize();
        return new Dimension(preferredSize.width, Math.max(preferredSize.height, myScrollPane.getViewport().getHeight()));
      }

      @Override
      public Color getBackground() {
        return CommitPanel.getCommitDetailsBackground();
      }

      @Override
      protected void paintChildren(Graphics g) {
        if (StringUtil.isNotEmpty(myEmptyText.getText())) {
          myEmptyText.paint(this, g);
        }
        else {
          super.paintChildren(g);
        }
      }
    };
    myEmptyText = new StatusText(myMainContentPanel) {
      @Override
      protected boolean isStatusVisible() {
        return StringUtil.isNotEmpty(getText());
      }
    };
    myMainContentPanel.setLayout(new BoxLayout(myMainContentPanel, BoxLayout.Y_AXIS));

    myMainContentPanel.setOpaque(false);
    myScrollPane.setViewportView(myMainContentPanel);
    myScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    myScrollPane.setViewportBorder(IdeBorderFactory.createEmptyBorder());

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), parent, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      @Override
      public Color getBackground() {
        return CommitPanel.getCommitDetailsBackground();
      }
    };
    myLoadingPanel.add(myScrollPane);

    setLayout(new BorderLayout());
    add(myLoadingPanel, BorderLayout.CENTER);

    myEmptyText.setText("Commit details");
  }

  @Override
  public void globalSchemeChange(EditorColorsScheme scheme) {
    for (int i = 0; i < mySelection.size(); i++) {
      CommitPanel commitPanel = getCommitPanel(i);
      commitPanel.update();
    }
  }

  @Override
  public Color getBackground() {
    return CommitPanel.getCommitDetailsBackground();
  }

  public void installCommitSelectionListener(@NotNull VcsLogGraphTable graphTable) {
    graphTable.getSelectionModel().addListSelectionListener(new CommitSelectionListenerForDetails(graphTable));
  }

  void updateDataPack(@NotNull VisiblePack dataPack) {
    myDataPack = dataPack;

    for (int i = 0; i < mySelection.size(); i++) {
      CommitPanel commitPanel = getCommitPanel(i);
      commitPanel.setDataPack(dataPack);
    }
  }

  public void branchesChanged() {
    for (int i = 0; i < mySelection.size(); i++) {
      CommitPanel commitPanel = getCommitPanel(i);
      commitPanel.updateBranches();
    }
  }

  private void rebuildCommitPanels(int[] selection) {
    myEmptyText.setText("");

    int selectionLength = selection.length;

    // for each commit besides the first there are two components: Separator and CommitPanel
    int existingCount = (myMainContentPanel.getComponentCount() + 1) / 2;
    int requiredCount = Math.min(selectionLength, MAX_ROWS);
    for (int i = existingCount; i < requiredCount; i++) {
      if (i > 0) {
        myMainContentPanel.add(new SeparatorComponent(0, OnePixelDivider.BACKGROUND, null));
      }
      myMainContentPanel.add(new CommitPanel(myLogData, myColorManager, myDataPack));
    }

    // clear superfluous items
    while (myMainContentPanel.getComponentCount() > 2 * requiredCount - 1) {
      myMainContentPanel.remove(myMainContentPanel.getComponentCount() - 1);
    }

    if (selectionLength > MAX_ROWS) {
      myMainContentPanel.add(new SeparatorComponent(0, OnePixelDivider.BACKGROUND, null));
      JBLabel label = new JBLabel("(showing " + MAX_ROWS + " of " + selectionLength + " selected commits)");
      label.setFont(VcsHistoryUtil.getCommitDetailsFont());
      label.setBorder(JBUI.Borders.empty(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2,
                                         myColorManager.isMultipleRoots()
                                         ? VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH +
                                           VcsLogGraphTable.ROOT_INDICATOR_COLORED_WIDTH
                                         : VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2, CommitPanel.BOTTOM_BORDER, 0));
      myMainContentPanel.add(label);
    }

    mySelection = Ints.asList(Arrays.copyOf(selection, requiredCount));

    repaint();
  }

  @NotNull
  private CommitPanel getCommitPanel(int index) {
    return (CommitPanel)myMainContentPanel.getComponent(2 * index);
  }

  private class CommitSelectionListenerForDetails extends CommitSelectionListener {
    public CommitSelectionListenerForDetails(VcsLogGraphTable graphTable) {
      super(DetailsPanel.this.myLogData, graphTable, DetailsPanel.this.myLoadingPanel);
    }

    @Override
    protected void onDetailsLoaded(@NotNull List<VcsFullCommitDetails> detailsList) {
      Set<VcsFullCommitDetails> newCommitDetails = ContainerUtil.newHashSet(detailsList);
      for (int i = 0; i < mySelection.size(); i++) {
        CommitPanel commitPanel = getCommitPanel(i);
        commitPanel.setCommit(detailsList.get(i));
      }

      if (!ContainerUtil.intersects(myCommitDetails, newCommitDetails)) {
        myScrollPane.getVerticalScrollBar().setValue(0);
      }
      myCommitDetails = newCommitDetails;
    }

    @Override
    protected void onSelection(@NotNull int[] selection) {
      rebuildCommitPanels(selection);
    }

    @Override
    protected void onEmptySelection() {
      myEmptyText.setText("No commits selected");
      myMainContentPanel.removeAll();
      mySelection = ContainerUtil.emptyList();
      myCommitDetails = Collections.emptySet();
    }

    @NotNull
    @Override
    protected List<Integer> getSelectionToLoad() {
      return mySelection;
    }
  }
}
