/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class DetailsPanel extends JPanel implements EditorColorsListener {
  private static final int MAX_ROWS = 50;
  private static final int MIN_SIZE = 20;

  @NotNull private final VcsLogData myLogData;

  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JPanel myMainContentPanel;
  @NotNull private final StatusText myEmptyText;

  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private List<Integer> mySelection = ContainerUtil.emptyList();
  @NotNull private Set<VcsFullCommitDetails> myCommitDetails = Collections.emptySet();

  public DetailsPanel(@NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager colorManager,
                      @NotNull Disposable parent) {
    myLogData = logData;
    myColorManager = colorManager;

    myScrollPane = new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myMainContentPanel = new MyMainContentPanel();
    myEmptyText = new StatusText(myMainContentPanel) {
      @Override
      protected boolean isStatusVisible() {
        return StringUtil.isNotEmpty(getText());
      }
    };
    myMainContentPanel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));

    myMainContentPanel.setOpaque(false);
    myScrollPane.setViewportView(myMainContentPanel);
    myScrollPane.setBorder(JBUI.Borders.empty());
    myScrollPane.setViewportBorder(JBUI.Borders.empty());

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
      myMainContentPanel.add(new CommitPanel(myLogData, myColorManager));
    }

    // clear superfluous items
    while (myMainContentPanel.getComponentCount() > 2 * requiredCount - 1) {
      myMainContentPanel.remove(myMainContentPanel.getComponentCount() - 1);
    }

    if (selectionLength > MAX_ROWS) {
      myMainContentPanel.add(new SeparatorComponent(0, OnePixelDivider.BACKGROUND, null));
      JBLabel label = new JBLabel("(showing " + MAX_ROWS + " of " + selectionLength + " selected commits)");
      label.setFont(VcsHistoryUtil.getCommitDetailsFont());
      label.setBorder(CommitPanel.getDetailsBorder());
      myMainContentPanel.add(label);
    }

    mySelection = Ints.asList(Arrays.copyOf(selection, requiredCount));

    repaint();
  }

  @NotNull
  private CommitPanel getCommitPanel(int index) {
    return (CommitPanel)myMainContentPanel.getComponent(2 * index);
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension minimumSize = super.getMinimumSize();
    return new Dimension(Math.max(minimumSize.width, JBUI.scale(MIN_SIZE)), Math.max(minimumSize.height, JBUI.scale(MIN_SIZE)));
  }

  private class CommitSelectionListenerForDetails extends CommitSelectionListener {
    public CommitSelectionListenerForDetails(VcsLogGraphTable graphTable) {
      super(DetailsPanel.this.myLogData, graphTable);
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
      final List<Integer> currentSelection = mySelection;
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        List<Collection<VcsRef>> result = ContainerUtil.newArrayList();
        for (Integer row : currentSelection) {
          result.add(myGraphTable.getModel().getRefsAtRow(row));
        }
        ApplicationManager.getApplication().invokeLater(() -> {
          if (currentSelection == mySelection) {
            for (int i = 0; i < currentSelection.size(); i++) {
              CommitPanel commitPanel = getCommitPanel(i);
              commitPanel.setRefs(result.get(i));
            }
          }
        });
      });
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

    @Override
    protected void startLoading() {
      myLoadingPanel.startLoading();
    }

    @Override
    protected void stopLoading() {
      myLoadingPanel.stopLoading();
    }
  }

  private class MyMainContentPanel extends ScrollablePanel {
    @Override
    public boolean getScrollableTracksViewportWidth() {
      boolean expanded = false;
      for (Component c : getComponents()) {
        if (c instanceof CommitPanel && ((CommitPanel)c).isExpanded()) {
          expanded = true;
          break;
        }
      }
      // expanded containing branches are displayed in a table, it is more convenient to have a scrollbar in this case
      return !expanded;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      int height = Math.max(preferredSize.height, myScrollPane.getViewport().getHeight());
      JBScrollPane scrollPane = UIUtil.getParentOfType(JBScrollPane.class, this);
      if (scrollPane == null || getScrollableTracksViewportWidth()) {
        return new Dimension(preferredSize.width, height);
      }
      else {
        // we want content panel to fill all available horizontal space in order to display root label in the upper-right corner
        // but when containing branches are expanded, we show a horizontal scrollbar, so content panel width wont be automatically adjusted
        // here it is done manually
        return new Dimension(Math.max(preferredSize.width, scrollPane.getViewport().getWidth()), height);
      }
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
  }
}
