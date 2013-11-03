package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Graph + the panel with branch labels above it.
 *
 * @author Kirill Likhodedov
 */
public class ActiveSurface extends JPanel implements TypeSafeDataProvider {

  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final BranchesPanel myBranchesPanel;
  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final Splitter myDetailsSplitter;
  @NotNull private final JBLoadingPanel myChangesLoadingPane;

  ActiveSurface(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUI vcsLogUI,
                @NotNull VcsLogSettings settings, @NotNull Project project) {
    myLogDataHolder = logDataHolder;
    myGraphTable = new VcsLogGraphTable(vcsLogUI, logDataHolder);
    myBranchesPanel = new BranchesPanel(logDataHolder, vcsLogUI);

    if (!settings.isShowBranchesPanel()) {
      myBranchesPanel.setVisible(false);
    }

    myDetailsPanel = new DetailsPanel(logDataHolder, myGraphTable, vcsLogUI.getColorManager());

    final ChangesBrowser changesBrowser = new RepositoryChangesBrowser(project, null, Collections.<Change>emptyList(), null);
    changesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myGraphTable);
    setDefaultEmptyText(changesBrowser);

    final CommitSelectionListener selectionChangeListener = new CommitSelectionListener(changesBrowser);
    myGraphTable.getSelectionModel().addListSelectionListener(selectionChangeListener);
    myGraphTable.getSelectionModel().addListSelectionListener(myDetailsPanel);
    myLogDataHolder.getMiniDetailsGetter().addDetailsLoadedListener(new Runnable() {
      @Override
      public void run() {
        myGraphTable.repaint();
      }
    });
    myLogDataHolder.getCommitDetailsGetter().addDetailsLoadedListener(new Runnable() {
      @Override
      public void run() {
        selectionChangeListener.valueChanged(null);
        myDetailsPanel.valueChanged(null);
      }
    });

    myChangesLoadingPane = new JBLoadingPanel(new BorderLayout(), project);
    myChangesLoadingPane.add(changesBrowser);

    myDetailsSplitter = new Splitter(true, 0.7f);
    myDetailsSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myGraphTable));

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setFirstComponent(myDetailsSplitter);
    splitter.setSecondComponent(myChangesLoadingPane);

    setLayout(new BorderLayout());
    add(myBranchesPanel, BorderLayout.NORTH);
    add(splitter, BorderLayout.CENTER);
  }

  private static void setDefaultEmptyText(ChangesBrowser changesBrowser) {
    changesBrowser.getViewer().setEmptyText("");
  }

  @NotNull
  public VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  @NotNull
  public BranchesPanel getBranchesPanel() {
    return myBranchesPanel;
  }

  // Provide data for show diff
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      if (myGraphTable.getSelectedRowCount() == 1) {
        List<Change> selectedChanges = getSelectedChanges();
        if (selectedChanges != null) {
          sink.put(VcsDataKeys.CHANGES, ArrayUtil.toObjectArray(selectedChanges, Change.class));
        }
      }
    }
  }

  public void setupDetailsSplitter(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  @Nullable
  public List<Change> getSelectedChanges() {
    return myGraphTable.getSelectedChanges();
  }

  private class CommitSelectionListener implements ListSelectionListener {
    private final ChangesBrowser myChangesBrowser;

    public CommitSelectionListener(ChangesBrowser changesBrowser) {
      myChangesBrowser = changesBrowser;
    }

    @Override
    public void valueChanged(@Nullable ListSelectionEvent notUsed) {
      int rows = myGraphTable.getSelectedRowCount();
      if (rows < 1) {
        myChangesLoadingPane.stopLoading();
        setDefaultEmptyText(myChangesBrowser);
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
      }
      else {
        List<Change> selectedChanges = getSelectedChanges();
        if (selectedChanges != null) {
          myChangesLoadingPane.stopLoading();
          myChangesBrowser.setChangesToDisplay(selectedChanges);
        }
        else {
          myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
          myChangesLoadingPane.startLoading();
        }
      }
    }
  }
}
