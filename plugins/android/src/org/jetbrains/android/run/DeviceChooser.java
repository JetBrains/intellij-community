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
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.BooleanCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Apr 3, 2009
 * Time: 6:02:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class DeviceChooser extends DialogWrapper implements AndroidDebugBridge.IDeviceChangeListener {
  public static final IDevice[] EMPTY_DEVICE_ARRAY = new IDevice[0];

  private final AndroidFacet myFacet;
  @Nullable
  private JPanel myPanel;
  private JTable myDeviceTable;
  private static final String[] COLUMN_TITLES = new String[]{"Serial Number", "AVD name", "State", "Compatible"};

  private int[] mySelectedRows;

  public DeviceChooser(@NotNull AndroidFacet facet, boolean multipleSelection, @Nullable String[] selectedSerials) {
    super(facet.getModule().getProject(), true);
    setTitle(AndroidBundle.message("choose.device.dialog.title"));
    init();
    myFacet = facet;
    DefaultTableModel defaultTableModel = new DefaultTableModel();
    myDeviceTable.setModel(defaultTableModel);
    myDeviceTable.setSelectionMode(multipleSelection ?
                                   ListSelectionModel.MULTIPLE_INTERVAL_SELECTION :
                                   ListSelectionModel.SINGLE_SELECTION);
    myDeviceTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateOkButton();
      }
    });
    myDeviceTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && isOKActionEnabled()) {
          doOKAction();
        }
      }
    });
    myDeviceTable.setDefaultRenderer(Boolean.class, new BooleanCellRenderer());
    myDeviceTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && isOKActionEnabled()) {
          doOKAction();
        }
      }
    });
    getOKAction().setEnabled(false);
    updateTable();
    if (selectedSerials != null) {
      resetSelection(selectedSerials);
    }
    /*try {
      AdbManager.run(new Runnable() {
        public void run() {
          AndroidDebugBridge.addDeviceChangeListener(DeviceChooser.this);
        }
      }, false);
    }
    catch (AdbNotRespondingException e) {
      Messages.showErrorDialog(facet.getModule().getProject(), e.getMessage(), CommonBundle.getErrorTitle());
    }*/
  }

  private void resetSelection(@NotNull String[] selectedSerials) {
    MyDeviceTableModel model = (MyDeviceTableModel)myDeviceTable.getModel();
    Set<String> selectedSerialsSet = new HashSet<String>();
    Collections.addAll(selectedSerialsSet, selectedSerials);
    IDevice[] myDevices = model.myDevices;
    ListSelectionModel selectionModel = myDeviceTable.getSelectionModel();
    selectionModel.clearSelection();
    for (int i = 0, n = myDevices.length; i < n; i++) {
      String serialNumber = myDevices[i].getSerialNumber();
      if (selectedSerialsSet.contains(serialNumber)) {
        selectionModel.addSelectionInterval(i, i);
      }
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDeviceTable;
  }

  private void updateTable() {
    final AndroidDebugBridge bridge = myFacet.getDebugBridge();
    IDevice[] devices = bridge != null ? bridge.getDevices() : EMPTY_DEVICE_ARRAY;
    int[] selectedRows = myDeviceTable.getSelectedRows();
    myDeviceTable.setModel(new MyDeviceTableModel(devices));
    if (selectedRows.length == 0 && devices.length > 0) {
      myDeviceTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    for (int selectedRow : selectedRows) {
      if (selectedRow < devices.length) {
        myDeviceTable.getSelectionModel().addSelectionInterval(selectedRow, selectedRow);
      }
    }
    updateOkButton();
  }

  private void updateOkButton() {
    IDevice[] devices = getSelectedDevices();
    boolean enabled = devices.length > 0;
    for (IDevice device : devices) {
      if (!device.isOnline()) {
        enabled = false;
      }
    }
    getOKAction().setEnabled(enabled);
  }

  @NotNull
  private static String getDeviceState(@NotNull IDevice device) {
    IDevice.DeviceState state = device.getState();
    return state != null ? capitalize(state.name().toLowerCase()) : "";
  }

  @Override
  protected void doOKAction() {
    mySelectedRows = myDeviceTable.getSelectedRows();
    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public IDevice[] getSelectedDevices() {
    int[] rows = mySelectedRows != null ? mySelectedRows : myDeviceTable.getSelectedRows();
    List<IDevice> result = new ArrayList<IDevice>();
    for (int row : rows) {
      if (row >= 0) {
        Object serial = myDeviceTable.getValueAt(row, 0);
        final AndroidDebugBridge bridge = myFacet.getDebugBridge();
        if (bridge == null) {
          return EMPTY_DEVICE_ARRAY;
        }
        IDevice[] devices = bridge.getDevices();
        for (IDevice device : devices) {
          if (device.getSerialNumber().equals(serial.toString())) {
            result.add(device);
            break;
          }
        }
      }
    }
    return result.toArray(new IDevice[result.size()]);
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{new RefreshAction(), new LaunchEmulatorAction(), getOKAction(), getCancelAction()};
  }

  private class LaunchEmulatorAction extends AbstractAction {
    public LaunchEmulatorAction() {
      putValue(NAME, "Launch Emulator");
    }

    public void actionPerformed(ActionEvent e) {
      AvdManager.AvdInfo avd = null;
      AvdManager manager = myFacet.getAvdManagerSilently();
      if (manager != null) {
        AvdChooser chooser = new AvdChooser(myFacet.getModule().getProject(), myFacet, manager, true, false);
        chooser.show();
        avd = chooser.getSelectedAvd();
        if (chooser.getExitCode() != OK_EXIT_CODE) return;
        if (avd == null) return;
      }
      myFacet.launchEmulator(avd != null ? avd.getName() : null, "", null);
    }
  }

  private class RefreshAction extends AbstractAction {
    RefreshAction() {
      putValue(NAME, "Refresh");
    }

    public void actionPerformed(ActionEvent e) {
      updateTable();
    }
  }

  public void deviceConnected(IDevice device) {
    /*ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        updateTable();
      }
    });*/
  }

  public void deviceDisconnected(IDevice device) {
    /*ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        updateTable();
      }
    });*/
  }

  public void deviceChanged(IDevice device, int changeMask) {
    /*if ((changeMask & (IDevice.CHANGE_STATE | IDevice.CHANGE_BUILD_INFO)) != 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          updateTable();
        }
      });
    }*/
  }

  private class MyDeviceTableModel extends AbstractTableModel {
    private final IDevice[] myDevices;

    public MyDeviceTableModel(IDevice[] devices) {
      myDevices = devices;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMN_TITLES[column];
    }

    public int getRowCount() {
      return myDevices.length;
    }

    public int getColumnCount() {
      return COLUMN_TITLES.length;
    }

    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex >= myDevices.length) {
        return null;
      }
      IDevice device = myDevices[rowIndex];
      switch (columnIndex) {
        case 0:
          return device.getSerialNumber();
        case 1:
          return device.getAvdName();
        case 2:
          return getDeviceState(device);
        case 3:
          return myFacet.isCompatibleDevice(device);
      }
      return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == 3) {
        return Boolean.class;
      }
      return String.class;
    }
  }
}
