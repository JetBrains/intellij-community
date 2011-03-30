/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.BooleanCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: May 9, 2009
 * Time: 5:33:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class AvdChooser extends DialogWrapper {
  private JTable myAvdTable;
  private JPanel myPanel;
  private JButton myRefreshButton;
  private JButton myCreateButton;
  private JButton myRemoveButton;
  private JButton myStartAvdManagerButton;
  private final AvdManager myAvdManager;
  private final AndroidFacet myFacet;
  private final boolean myCompatibleAvd;
  private final boolean myMustSelect;

  private static final String[] COLUMN_TITLES = new String[]{"Name", "Target", "Platform", "API Level", "Valid", "Compatible"};

  @Nullable
  private static String getAndroidToolPath(@NotNull AndroidFacet facet) {
    AndroidSdk sdk = facet.getConfiguration().getAndroidSdk();
    if (sdk == null) return null;
    String androidCmd = SdkConstants.androidCmdName();
    return sdk.getLocation() + File.separator + AndroidUtils.toolPath(androidCmd);
  }

  public AvdChooser(@NotNull final Project project,
                    @NotNull final AndroidFacet facet,
                    @NotNull AvdManager avdManager,
                    boolean mustSelect,
                    boolean compatibleAvd) {
    super(project, true);
    myMustSelect = mustSelect;
    setTitle(AndroidBundle.message("avd.dialog.title"));
    init();
    myAvdManager = avdManager;
    myFacet = facet;
    myAvdTable.setModel(new MyAvdTableModel(myFacet.getAllAvds()));
    myAvdTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myCompatibleAvd = compatibleAvd;
    myRefreshButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateTable();
      }
    });
    myCreateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        createAvd(project, facet);
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        removeSelectedAvd();
      }
    });
    myAvdTable.setDefaultRenderer(Boolean.class, new BooleanCellRenderer());
    myAvdTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && isOKActionEnabled()) {
          doOKAction();
        }
      }
    });
    final String androidToolPath = getAndroidToolPath(facet);
    myStartAvdManagerButton.setEnabled(new File(androidToolPath).exists());
    myStartAvdManagerButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(androidToolPath);
        AndroidUtils.runExternalToolInSeparateThread(project, commandLine);
      }
    });
    updateTable();
    ListSelectionModel selectionModel = myAvdTable.getSelectionModel();
    if (mustSelect) {
      if (myAvdTable.getModel().getRowCount() > 0) {
        selectionModel.setSelectionInterval(0, 0);
      }
    }
    selectionModel.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateOkAction();
      }
    });
    updateOkAction();
    myAvdTable.requestFocus();
  }

  private void updateOkAction() {
    AvdManager.AvdInfo avd = getSelectedAvd();
    getOKAction().setEnabled(avd != null ? avd.getStatus() == AvdManager.AvdInfo.AvdStatus.OK : myMustSelect);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAvdTable;
  }

  private void createAvd(Project project, AndroidFacet facet) {
    CreateAvdDialog dialog = new CreateAvdDialog(project, facet, myAvdManager, false, false);
    dialog.show();
    if (dialog.getExitCode() == OK_EXIT_CODE) {
      updateTable();
    }
  }

  private boolean isAvdBusy(@NotNull AvdManager.AvdInfo avd) {
    final AndroidDebugBridge bridge = myFacet.getDebugBridge();
    if (bridge == null) return false;
    IDevice[] devices = bridge.getDevices();
    for (IDevice device : devices) {
      String avdName = device.getAvdName();
      if (avdName != null && avdName.equals(avd.getName())) {
        return true;
      }
    }
    return false;
  }

  private void removeSelectedAvd() {
    AvdManager.AvdInfo selectedAvd = getSelectedAvd();
    if (selectedAvd != null) {
      if (isAvdBusy(selectedAvd)) {
        Messages.showErrorDialog(myPanel, AndroidBundle.message("cant.remove.avd.error"));
      }
      else {
        myAvdManager.deleteAvd(selectedAvd, AndroidUtils.getSdkLog(myPanel));
        updateTable();
      }
    }
  }

  private void updateTable() {
    int selected = myAvdTable.getSelectedRow();
    myAvdTable.setModel(new MyAvdTableModel(myFacet.getAllAvds()));
    myAvdTable.getSelectionModel().setSelectionInterval(selected, selected);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  public AvdManager.AvdInfo getSelectedAvd() {
    MyAvdTableModel model = (MyAvdTableModel)myAvdTable.getModel();
    int selectedRow = myAvdTable.getSelectedRow();
    return selectedRow >= 0 && selectedRow < model.myInfos.length ? model.myInfos[selectedRow] : null;
  }

  public void setSelectedAvd(@NotNull String avdName) {
    MyAvdTableModel model = (MyAvdTableModel)myAvdTable.getModel();
    for (int i = 0; i < model.myInfos.length; i++) {
      AvdManager.AvdInfo info = model.myInfos[i];
      if (info.getName().equals(avdName)) {
        myAvdTable.getSelectionModel().setSelectionInterval(i, i);
      }
    }
  }

  private class MyAvdTableModel extends AbstractTableModel {
    private final AvdManager.AvdInfo[] myInfos;

    public MyAvdTableModel(AvdManager.AvdInfo[] infos) {
      myInfos = infos;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMN_TITLES[column];
    }

    public int getRowCount() {
      return myInfos.length;
    }

    public int getColumnCount() {
      return COLUMN_TITLES.length;
    }

    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      AvdManager.AvdInfo info = myInfos[rowIndex];
      IAndroidTarget target = info.getTarget();
      final String unknown = "<unknown>";
      switch (columnIndex) {
        case 0:
          return info.getName();
        case 1:
          return target != null ? target.getName() : unknown;
        case 2:
          return target != null ? target.getVersionName() : unknown;
        case 3:
          return target != null ? target.getVersion().getApiString() : unknown;
        case 4:
          return info.getStatus() == AvdManager.AvdInfo.AvdStatus.OK;
        case 5:
          return myFacet.isCompatibleAvd(info);
      }
      return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == 4 || columnIndex == 5) {
        return Boolean.class;
      }
      return String.class;
    }
  }

  @Override
  protected String getHelpId() {
    return "reference.android.selectAVD";
  }

  @Override
  protected void doOKAction() {
    AvdManager.AvdInfo selectedAvd = getSelectedAvd();
    if (myCompatibleAvd && selectedAvd != null && !myFacet.isCompatibleAvd(selectedAvd)) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("select.compatible.avd.error"));
      return;
    }
    super.doOKAction();
  }

}
