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
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.vcs.commit.CommitMessageInspectionProfile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.CommitPresentation;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.TroveUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.buildPresentation;

/**
 * @author Kirill Likhodedov
 */
public class DetailsPanel extends JPanel implements EditorColorsListener, Disposable {
  private static final int MAX_ROWS = 50;
  private static final int MIN_SIZE = 20;
  private static final String EMPTY_TEXT = "Select commits to view details";

  @NotNull private final VcsLogData myLogData;

  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JPanel myMainContentPanel;
  @NotNull private final StatusText myEmptyText;

  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private List<Integer> mySelection = ContainerUtil.emptyList();
  @NotNull private TIntHashSet myCommitIds = new TIntHashSet();
  @Nullable private ProgressIndicator myResolveIndicator = null;

  public DetailsPanel(@NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager colorManager,
                      @NotNull Disposable parent) {
    myLogData = logData;
    myColorManager = colorManager;

    myScrollPane = new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myMainContentPanel = new MyMainContentPanel();
    myEmptyText = new StatusText(this) {
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

    ProjectInspectionProfileManager.getInstance(logData.getProject()).addProfileChangeListener(new ProfileChangeAdapter() {
      @Override
      public void profileChanged(@Nullable InspectionProfile profile) {
        if (CommitMessageInspectionProfile.getInstance(myLogData.getProject()).equals(profile)) {
          // only update after settings dialog is closed and settings are actually applied
          ApplicationManager.getApplication().invokeLater(DetailsPanel.this::update, ModalityState.NON_MODAL);
        }
      }
    }, this);

    myEmptyText.setText(EMPTY_TEXT);
    Disposer.register(parent, this);
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

  @Override
  public void globalSchemeChange(EditorColorsScheme scheme) {
    update();
  }

  private void update() {
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

  protected void navigate(@NotNull CommitId commitId) {
  }

  private void rebuildCommitPanels(int[] selection) {
    myEmptyText.setText(selection.length == 0 ? EMPTY_TEXT : "");

    int selectionLength = selection.length;

    // for each commit besides the first there are two components: Separator and CommitPanel
    int existingCount = (myMainContentPanel.getComponentCount() + 1) / 2;
    int requiredCount = Math.min(selectionLength, MAX_ROWS);
    for (int i = existingCount; i < requiredCount; i++) {
      if (i > 0) {
        myMainContentPanel.add(new SeparatorComponent(0, OnePixelDivider.BACKGROUND, null));
      }
      myMainContentPanel.add(new CommitPanel(myLogData, myColorManager, this::navigate));
    }

    // clear superfluous items
    while (myMainContentPanel.getComponentCount() > 2 * requiredCount - 1) {
      myMainContentPanel.remove(myMainContentPanel.getComponentCount() - 1);
    }

    if (selectionLength > MAX_ROWS) {
      myMainContentPanel.add(new SeparatorComponent(0, OnePixelDivider.BACKGROUND, null));
      JBLabel label = new JBLabel("(showing " + MAX_ROWS + " of " + selectionLength + " selected commits)");
      label.setFont(FontUtil.getCommitMetadataFont());
      label.setBorder(JBUI.Borders.emptyLeft(CommitPanel.SIDE_BORDER));
      myMainContentPanel.add(label);
    }

    mySelection = Ints.asList(Arrays.copyOf(selection, requiredCount));

    repaint();
  }

  private void resolveHashes(@NotNull List<CommitId> ids,
                             @NotNull List<CommitPresentation> presentations,
                             @NotNull Set<String> unResolvedHashes,
                             @NotNull Condition<Object> expired) {
    if (!unResolvedHashes.isEmpty()) {
      myResolveIndicator = BackgroundTaskUtil.executeOnPooledThread(this, () -> {
        MultiMap<String, CommitId> resolvedHashes = MultiMap.createSmart();

        Set<String> fullHashes =
          ContainerUtil.newHashSet(ContainerUtil.filter(unResolvedHashes, h -> h.length() == HashImpl.FULL_HASH_LENGTH));
        for (String fullHash: fullHashes) {
          Hash hash = HashImpl.build(fullHash);
          for (VirtualFile root: myLogData.getRoots()) {
            CommitId id = new CommitId(hash, root);
            if (myLogData.getStorage().containsCommit(id)) {
              resolvedHashes.putValue(fullHash, id);
            }
          }
        }
        unResolvedHashes.removeAll(fullHashes);

        if (!unResolvedHashes.isEmpty()) {
          myLogData.getStorage().iterateCommits(commitId -> {

            for (String hashString: unResolvedHashes) {
              if (StringUtil.startsWithIgnoreCase(commitId.getHash().asString(), hashString)) {
                resolvedHashes.putValue(hashString, commitId);
              }
            }
            return false;
          });
        }

        List<CommitPresentation> resolvedPresentations = ContainerUtil.map2List(presentations,
                                                                                presentation -> presentation.resolve(resolvedHashes));
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        ApplicationManager.getApplication().invokeLater(() -> {
                                                          myResolveIndicator = null;
                                                          setPresentations(ids, resolvedPresentations);
                                                        },
                                                        Conditions.or(o -> myResolveIndicator != indicator, expired));
      });
    }
  }

  private void cancelResolve() {
    if (myResolveIndicator != null) {
      myResolveIndicator.cancel();
      myResolveIndicator = null;
    }
  }

  private void setPresentations(@NotNull List<CommitId> ids,
                                @NotNull List<? extends CommitPresentation> presentations) {
    assert ids.size() == presentations.size();
    for (int i = 0; i < mySelection.size(); i++) {
      CommitPanel commitPanel = getCommitPanel(i);
      commitPanel.setCommit(ids.get(i), presentations.get(i));
    }
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

  @Override
  public void dispose() {
    cancelResolve();
  }

  private class CommitSelectionListenerForDetails extends CommitSelectionListener {
    public CommitSelectionListenerForDetails(VcsLogGraphTable graphTable) {
      super(DetailsPanel.this.myLogData, graphTable);
    }

    @Override
    protected void onDetailsLoaded(@NotNull List<VcsFullCommitDetails> detailsList) {
      List<CommitId> ids = ContainerUtil.map(detailsList,
                                             detail -> new CommitId(detail.getId(), detail.getRoot()));
      Set<String> unResolvedHashes = ContainerUtil.newHashSet();
      List<CommitPresentation> presentations = ContainerUtil.map(detailsList,
                                                                 detail -> buildPresentation(myLogData.getProject(), detail,
                                                                                             unResolvedHashes));
      setPresentations(ids, presentations);

      TIntHashSet newCommitIds = TroveUtil.map2IntSet(detailsList, c -> myLogData.getStorage().getCommitIndex(c.getId(), c.getRoot()));
      if (!TroveUtil.intersects(myCommitIds, newCommitIds)) {
        myScrollPane.getVerticalScrollBar().setValue(0);
      }
      myCommitIds = newCommitIds;

      List<Integer> currentSelection = mySelection;
      resolveHashes(ids, presentations, unResolvedHashes, o -> currentSelection != mySelection);
    }

    @Override
    protected void onSelection(@NotNull int[] selection) {
      cancelResolve();
      rebuildCommitPanels(selection);
      List<Integer> currentSelection = mySelection;
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        List<Collection<VcsRef>> result = ContainerUtil.newArrayList();
        for (Integer row: currentSelection) {
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
      cancelResolve();
      setEmpty(EMPTY_TEXT);
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

    @Override
    protected void onError(@NotNull Throwable error) {
      setEmpty("Error loading commits");
    }

    private void setEmpty(@NotNull String text) {
      myEmptyText.setText(text);
      myMainContentPanel.removeAll();
      mySelection = ContainerUtil.emptyList();
      myCommitIds = new TIntHashSet();
    }
  }

  private static class MyMainContentPanel extends ScrollablePanel {
    @Override
    public Insets getInsets() {
      // to fight ViewBorder
      return JBUI.emptyInsets();
    }

    @Override
    public Color getBackground() {
      return CommitPanel.getCommitDetailsBackground();
    }
  }
}
