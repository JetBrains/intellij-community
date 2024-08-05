// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.ui;

import com.google.common.primitives.Ints;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.mq.HgQDeleteCommand;
import org.zmlx.hg4idea.command.mq.HgQRenameCommand;
import org.zmlx.hg4idea.mq.HgMqAdditionalPatchReader;
import org.zmlx.hg4idea.mq.MqPatchDetails;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HgMqUnAppliedPatchesPanel extends JPanel implements UiDataProvider, HgUpdater, Disposable {

  public static final DataKey<HgMqUnAppliedPatchesPanel> MQ_PATCHES = DataKey.create("Mq.Patches");
  private static final String POPUP_ACTION_GROUP = "Mq.Patches.ContextMenu";
  private static final String TOOLBAR_ACTION_GROUP = "Mq.Patches.Toolbar";
  private static final Logger LOG = Logger.getInstance(HgMqUnAppliedPatchesPanel.class);
  private static final String START_EDITING = "startEditing";

  private final @NotNull Project myProject;
  private final @NotNull HgRepository myRepository;
  private final @NotNull MyPatchTable myPatchTable;
  private final @Nullable VirtualFile myMqPatchDir;
  private volatile boolean myNeedToUpdateFileContent;
  private final @Nullable File mySeriesFile;

  public HgMqUnAppliedPatchesPanel(@NotNull HgRepository repository) {
    super(new BorderLayout());
    myRepository = repository;
    myProject = myRepository.getProject();
    myMqPatchDir = myRepository.getHgDir().findChild("patches");
    mySeriesFile = myMqPatchDir != null ? new File(myMqPatchDir.getPath(), "series") : null;

    myPatchTable = new MyPatchTable(new MyPatchModel(myRepository.getUnappliedPatchNames()));
    myPatchTable.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        updatePatchSeriesInBackground(null);
        super.focusLost(e);
      }
    });
    myPatchTable.setShowColumns(true);
    myPatchTable.setFillsViewportHeight(true);
    myPatchTable.getEmptyText().setText(IdeCoreBundle.message("message.nothingToShow"));
    myPatchTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), START_EDITING);
    myPatchTable.setDragEnabled(true);
    TableSpeedSearch.installOn(myPatchTable);
    myPatchTable.setDropMode(DropMode.INSERT_ROWS);
    myPatchTable.setTransferHandler(new TableRowsTransferHandler(myPatchTable));

    add(createToolbar(), BorderLayout.WEST);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPatchTable);
    add(scrollPane, BorderLayout.CENTER);
    myProject.getMessageBus().connect(this).subscribe(HgVcs.STATUS_TOPIC, this);
  }

  @Override
  public void dispose() {
  }

  private JComponent createToolbar() {
    MqRefreshAction mqRefreshAction = new MqRefreshAction();
    ActionUtil.mergeFrom(mqRefreshAction, "hg4idea.QRefresh");

    MqDeleteAction mqDeleteAction = new MqDeleteAction();
    ActionUtil.mergeFrom(mqDeleteAction, "hg4idea.QDelete");

    PopupHandler.installPopupMenu(myPatchTable, POPUP_ACTION_GROUP, ActionPlaces.PROJECT_VIEW_POPUP);

    ActionManager actionManager = ActionManager.getInstance();

    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(mqRefreshAction);
    toolbarGroup.add(actionManager.getAction("Hg.MQ.Unapplied"));
    toolbarGroup.add(mqDeleteAction);

    ActionToolbar toolbar = actionManager.createActionToolbar(TOOLBAR_ACTION_GROUP, toolbarGroup, false);
    toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  @RequiresEdt
  public void updatePatchSeriesInBackground(final @Nullable Runnable runAfterUpdate) {
    final String newContent = myNeedToUpdateFileContent ? getContentFromModel() : null;
    myNeedToUpdateFileContent = false;
    new Task.Backgroundable(myProject, HgBundle.message("action.hg4idea.mq.updating", myRepository.getPresentableUrl())) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (newContent != null) {
          writeSeriesFile(newContent);
        }
        if (runAfterUpdate != null) {
          runAfterUpdate.run();
        }
      }
    }.queue();
  }

  private void writeSeriesFile(@NotNull String newContent) {
    if (mySeriesFile == null || !mySeriesFile.exists()) return;
    try {
      FileUtil.writeToFile(mySeriesFile, newContent);
    }
    catch (IOException e1) {
      LOG.error("Could not modify mq series file", e1);
    }
    myRepository.update();
  }

  @RequiresEdt
  private @NotNull String getContentFromModel() {
    StringBuilder content = new StringBuilder();
    String separator = "\n";
    StringUtil.join(HgUtil.getNamesWithoutHashes(myRepository.getMQAppliedPatches()), separator, content);
    content.append(separator);
    //append unapplied patches
    for (int i = 0; i < myPatchTable.getRowCount(); i++) {
      content.append(getPatchName(i)).append(separator);
    }
    return content.toString();
  }

  @RequiresEdt
  private String getPatchName(int i) {
    return myPatchTable.getModel().getPatchName(i);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HgMqUnAppliedPatchesPanel panel)) return false;

    if (!myRepository.equals(panel.myRepository)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myRepository.hashCode();
  }

  @RequiresEdt
  public @NotNull List<String> getSelectedPatchNames() {
    return getPatchNames(myPatchTable.getSelectedRows());
  }

  @CalledInAny
  private @NotNull List<String> getPatchNames(int[] rows) {
    return ContainerUtil.map(Ints.asList(rows), integer -> getPatchName(integer));
  }

  public @NotNull HgRepository getRepository() {
    return myRepository;
  }

  public int getSelectedRowsCount() {
    return myPatchTable.getSelectedRowCount();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(MQ_PATCHES, this);
    String patchName = getPatchName(myPatchTable.getSelectedRow());
    if (myMqPatchDir != null && myPatchTable.getSelectedRowCount() == 1) {
      sink.lazy(CommonDataKeys.VIRTUAL_FILE, () -> {
        return VfsUtil.findFileByIoFile(new File(myMqPatchDir.getPath(), patchName), true);
      });
    }
  }

  @Override
  public void update(final Project project, @Nullable VirtualFile root) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!myProject.isDisposed()) {
        refreshAll();
      }
    });
  }

  private class MqDeleteAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<String> names = getSelectedPatchNames();
      if (names.isEmpty()) return;

      if (Messages.showOkCancelDialog(myRepository.getProject(),
                                      HgBundle.message("action.hg4idea.mq.delete.confirmation", names.size()),
                                      HgBundle.message("delete.confirmation.title"), Messages.getWarningIcon()) == Messages.OK) {
        Runnable deleteTask = () -> {
          ProgressManager.getInstance().getProgressIndicator().setText(HgBundle.message("action.hg4idea.mq.delete.progress"));
          new HgQDeleteCommand(myRepository).executeInCurrentThread(names);
        };
        updatePatchSeriesInBackground(deleteTask);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedRowsCount() != 0 && !myPatchTable.isEditing());
    }
  }

  private class MqRefreshAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      refreshAll();
    }
  }

  private void refreshAll() {
    updateModel();
  }

  private void updateModel() {
    MyPatchModel model = myPatchTable.getModel();
    model.updatePatches(myRepository.getUnappliedPatchNames());
  }

  private class MyPatchModel extends AbstractTableModel implements MultiReorderedModel {

    private final MqPatchDetails.MqPatchEnum @NotNull [] myColumnNames = MqPatchDetails.MqPatchEnum.values();
    private final @NotNull Map<String, MqPatchDetails> myPatchesWithDetails = new HashMap<>();
    private final @NotNull List<String> myPatches;

    MyPatchModel(@NotNull List<String> names) {
      myPatches = new ArrayList<>(names);
      readMqPatchesDetails();
    }

    private void readMqPatchesDetails() {
      for (String name : myPatches) {
        File patchFile = myMqPatchDir != null ? new File(myMqPatchDir.getPath(), name) : null;
        myPatchesWithDetails.put(name, HgMqAdditionalPatchReader.readMqPatchInfo(myRepository.getRoot(), patchFile));
      }
    }

    @Override
    public int getColumnCount() {
      return myColumnNames.length;
    }

    @Override
    public String getColumnName(int col) {
      return myColumnNames[col].getColumnName();
    }

    @Override
    public Class getColumnClass(int c) {
      return getValueAt(0, c).getClass();
    }

    @Override
    public int getRowCount() {
      return myPatches.size();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      String name = getPatchName(rowIndex);
      if (columnIndex == 0) return name;
      MqPatchDetails patchDetails = myPatchesWithDetails.get(name);
      String mapDetail = patchDetails != null ? patchDetails.getPresentationDataFor(myColumnNames[columnIndex]) : "";
      return mapDetail != null ? mapDetail : "";
    }

    private @NotNull String getPatchName(int rowIndex) {
      return myPatches.get(rowIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      String newPatchName = (String)aValue;
      myPatchesWithDetails.put(newPatchName, myPatchesWithDetails.remove(myPatches.get(rowIndex)));
      myPatches.set(rowIndex, newPatchName);
    }

    public void updatePatches(List<String> newNames) {
      myPatches.clear();
      myPatches.addAll(newNames);
      myPatchesWithDetails.clear();
      readMqPatchesDetails();
      fireTableDataChanged();
    }

    @Override
    public boolean canMoveRows() {
      return true;
    }

    @Override
    public int[] moveRows(int[] rowsIndexes, int destination) {
      List<String> names = getPatchNames(rowsIndexes);
      myPatches.removeAll(names);
      int[] selection = new int[rowsIndexes.length];
      for (int i = 0; i < rowsIndexes.length; i++) {
        selection[i] = destination;
        myPatches.add(destination++, names.get(i));
      }
      myNeedToUpdateFileContent = true;
      fireTableDataChanged();
      return selection;
    }
  }

  private class MyPatchTable extends JBTable {
    MyPatchTable(MyPatchModel model) {
      super(model);
    }

    @Override
    public MyPatchModel getModel() {
      return (MyPatchModel)dataModel;
    }

    @Override
    public void editingStopped(ChangeEvent e) {
      final int editingRow = getEditingRow();
      final String oldName = getModel().getPatchName(editingRow);
      super.editingStopped(e);
      updatePatchSeriesInBackground(() -> HgQRenameCommand.performPatchRename(myRepository, oldName, getModel().getPatchName(editingRow)));
    }
  }
}
