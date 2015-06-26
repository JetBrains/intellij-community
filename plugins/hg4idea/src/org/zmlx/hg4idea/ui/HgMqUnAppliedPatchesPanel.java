/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.ui;

import com.google.common.primitives.Ints;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.ui.RowsDnDSupport;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.List;
import java.util.Map;

public class HgMqUnAppliedPatchesPanel extends JPanel implements DataProvider, HgUpdater {

  public static final DataKey<HgMqUnAppliedPatchesPanel> MQ_PATCHES = DataKey.create("Mq.Patches");
  private static final String POPUP_ACTION_GROUP = "Mq.Patches.ContextMenu";
  private static final String TOOLBAR_ACTION_GROUP = "Mq.Patches.Toolbar";
  private static final Logger LOG = Logger.getInstance(HgMqUnAppliedPatchesPanel.class);
  private static final String START_EDITING = "startEditing";

  @NotNull private final Project myProject;
  @NotNull private final HgRepository myRepository;
  @NotNull private final MyPatchTable myPatchTable;
  @Nullable private final VirtualFile myMqPatchDir;
  private volatile boolean myNeedToUpdateFileContent;
  @Nullable private final File mySeriesFile;

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
    myPatchTable.getEmptyText().setText("Nothing to show");
    myPatchTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), START_EDITING);
    RowsDnDSupport.install(myPatchTable, myPatchTable.getModel());
    new TableSpeedSearch(myPatchTable);

    add(createToolbar(), BorderLayout.WEST);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myPatchTable);
    add(scrollPane, BorderLayout.CENTER);
    myProject.getMessageBus().connect(myProject).subscribe(HgVcs.STATUS_TOPIC, this);
  }

  private JComponent createToolbar() {
    MqRefreshAction mqRefreshAction = new MqRefreshAction();
    EmptyAction.setupAction(mqRefreshAction, "hg4idea.QRefresh", this);

    MqDeleteAction mqDeleteAction = new MqDeleteAction();
    EmptyAction.setupAction(mqDeleteAction, "hg4idea.QDelete", this);

    PopupHandler.installPopupHandler(myPatchTable, POPUP_ACTION_GROUP, ActionPlaces.PROJECT_VIEW_POPUP);

    ActionManager actionManager = ActionManager.getInstance();

    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(mqRefreshAction);
    toolbarGroup.add(actionManager.getAction("Hg.MQ.Unapplied"));
    toolbarGroup.add(mqDeleteAction);

    ActionToolbar toolbar = actionManager.createActionToolbar(TOOLBAR_ACTION_GROUP, toolbarGroup, false);
    toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  @CalledInAwt
  public void updatePatchSeriesInBackground(@Nullable final Runnable runAfterUpdate) {
    final String newContent = myNeedToUpdateFileContent ? getContentFromModel() : null;
    myNeedToUpdateFileContent = false;
    new Task.Backgroundable(myProject, "Updating patch series for " + myRepository.getPresentableUrl()) {
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

  @NotNull
  @CalledInAwt
  private String getContentFromModel() {
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

  @CalledInAwt
  private String getPatchName(int i) {
    return myPatchTable.getModel().getPatchName(i);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HgMqUnAppliedPatchesPanel)) return false;

    HgMqUnAppliedPatchesPanel panel = (HgMqUnAppliedPatchesPanel)o;

    if (!myRepository.equals(panel.myRepository)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myRepository.hashCode();
  }

  @Nullable
  private VirtualFile getSelectedPatchFile() {
    if (myMqPatchDir == null || myPatchTable.getSelectedRowCount() != 1) return null;
    String patchName = getPatchName(myPatchTable.getSelectedRow());
    return VfsUtil.findFileByIoFile(new File(myMqPatchDir.getPath(), patchName), true);
  }

  @NotNull
  @CalledInAwt
  public List<String> getSelectedPatchNames() {
    return ContainerUtil.map(Ints.asList(myPatchTable.getSelectedRows()), new Function<Integer, String>() {
      @Override
      public String fun(Integer integer) {
        return getPatchName(integer);
      }
    });
  }

  @NotNull
  public HgRepository getRepository() {
    return myRepository;
  }

  public int getSelectedRowsCount() {
    return myPatchTable.getSelectedRowCount();
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (MQ_PATCHES.is(dataId)) {
      return this;
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      VirtualFile patchVFile = getSelectedPatchFile();
      if (patchVFile != null) return patchVFile;
    }
    return null;
  }

  @Override
  public void update(final Project project, @Nullable VirtualFile root) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (project != null && !project.isDisposed()) {
          refreshAll();
        }
      }
    });
  }

  private class MqDeleteAction extends DumbAwareAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      final List<String> names = getSelectedPatchNames();
      if (names.isEmpty()) return;

      if (Messages.showOkCancelDialog(myRepository.getProject(), String
                                        .format("You are going to delete selected %s. Would you like to continue?",
                                                StringUtil.pluralize("patch", names.size())),
                                      "Delete Confirmation", Messages.getWarningIcon()) == Messages.OK) {
        Runnable deleteTask = new Runnable() {
          @Override
          public void run() {
            ProgressManager.getInstance().getProgressIndicator().setText("Deleting patches...");
            new HgQDeleteCommand(myRepository).execute(names);
          }
        };
        updatePatchSeriesInBackground(deleteTask);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedRowsCount() != 0 && !myPatchTable.isEditing());
    }
  }

  private class MqRefreshAction extends DumbAwareAction {
    public void actionPerformed(AnActionEvent e) {
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

  private class MyPatchModel extends AbstractTableModel implements EditableModel {

    @NotNull private final MqPatchDetails.MqPatchEnum[] myColumnNames = MqPatchDetails.MqPatchEnum.values();
    @NotNull private final Map<String, MqPatchDetails> myPatchesWithDetails = ContainerUtil.newHashMap();
    @NotNull private final List<String> myPatches;

    public MyPatchModel(@NotNull List<String> names) {
      myPatches = ContainerUtil.newArrayList(names);
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
      return myColumnNames[col].toString();
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

    @NotNull
    private String getPatchName(int rowIndex) {
      return myPatches.get(rowIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      String newPatchName = (String)aValue;
      myPatchesWithDetails.put(newPatchName, myPatchesWithDetails.remove(myPatches.get(rowIndex)));
      myPatches.set(rowIndex, newPatchName);
    }

    @Override
    public void addRow() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      String tmp = myPatches.get(oldIndex);
      myPatches.remove(oldIndex);
      myPatches.add(newIndex, tmp);
      myNeedToUpdateFileContent = true;
      fireTableDataChanged();
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }

    @Override
    public void removeRow(int idx) {
      throw new UnsupportedOperationException();
    }

    public void updatePatches(List<String> newNames) {
      myPatches.clear();
      myPatches.addAll(newNames);
      myPatchesWithDetails.clear();
      readMqPatchesDetails();
      fireTableDataChanged();
    }
  }

  private class MyPatchTable extends JBTable {
    public MyPatchTable(MyPatchModel model) {
      super(model);
    }

    public MyPatchModel getModel() {
      return (MyPatchModel)dataModel;
    }

    @Override
    public void editingStopped(ChangeEvent e) {
      int editingRow = getEditingRow();
      String oldName = getModel().getPatchName(editingRow);
      super.editingStopped(e);
      HgQRenameCommand.performPatchRename(myRepository, oldName, getModel().getPatchName(editingRow));
    }
  }
}
