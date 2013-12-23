/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.StripeTable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsConfigurablePanel extends JPanel implements Disposable {
  private NotificationsTable myTable;
  private static final String REMOVE_KEY = "REMOVE";
  private final JCheckBox myDisplayBalloons;

  public NotificationsConfigurablePanel() {
    setLayout(new BorderLayout());
    myTable = new NotificationsTable();

    JScrollPane scrollPane = new JBScrollPane(myTable);
    scrollPane.setBorder(new LineBorder(UIUtil.getBorderColor()));
    add(scrollPane, BorderLayout.CENTER);
    myDisplayBalloons = new JCheckBox("Display balloon notifications");
    myDisplayBalloons.setMnemonic('b');
    add(myDisplayBalloons, BorderLayout.NORTH);
    myDisplayBalloons.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTable.repaint();
      }

    });

    myTable.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), REMOVE_KEY);
    myTable.getActionMap().put(REMOVE_KEY, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        removeSelected();
      }
    });
  }

  private void removeSelected() {
    myTable.removeSelected();
  }

  public void dispose() {
    myTable = null;
  }

  public boolean isModified() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      if (settingsWrapper.hasChanged()) {
        return true;
      }
    }

    return NotificationsConfigurationImpl.getNotificationsConfigurationImpl().SHOW_BALLOONS != myDisplayBalloons.isSelected();
  }

  public void apply() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.apply();
    }

    NotificationsConfigurationImpl.getNotificationsConfigurationImpl().SHOW_BALLOONS = myDisplayBalloons.isSelected();
  }

  public void reset() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.reset();
    }
    
    myDisplayBalloons.setSelected(NotificationsConfigurationImpl.getNotificationsConfigurationImpl().SHOW_BALLOONS);

    myTable.invalidate();
    myTable.repaint();
  }

  private class NotificationsTable extends StripeTable {
    private static final int ID_COLUMN = 0;
    private static final int DISPLAY_TYPE_COLUMN = 1;
    private static final int LOG_COLUMN = 2;
    private static final int READ_ALOUD_COLUMN = 3;

    public NotificationsTable() {
      super(new NotificationsTableModel());

      final TableColumn idColumn = getColumnModel().getColumn(ID_COLUMN);
      idColumn.setPreferredWidth(200);

      final TableColumn displayTypeColumn = getColumnModel().getColumn(DISPLAY_TYPE_COLUMN);
      displayTypeColumn.setMaxWidth(300);
      displayTypeColumn.setPreferredWidth(250);
      displayTypeColumn.setCellRenderer(new ComboBoxTableRenderer<NotificationDisplayType>(NotificationDisplayType.values()) {
        @Override
        protected void customizeComponent(NotificationDisplayType value, JTable table, boolean isSelected) {
          super.customizeComponent(value, table, isSelected);
          if (!myDisplayBalloons.isSelected() && !isSelected) {
            setBackground(UIUtil.getComboBoxDisabledBackground());
            setForeground(UIUtil.getComboBoxDisabledForeground());
          }
        }

        @Override
        protected String getTextFor(@NotNull NotificationDisplayType value) {
          return value.getTitle();
        }
      });

      displayTypeColumn.setCellEditor(new ComboBoxTableRenderer<NotificationDisplayType>(NotificationDisplayType.values()) {

        @Override
        public boolean isCellEditable(EventObject event) {
          if (!myDisplayBalloons.isSelected()) {
            return false;
          }

          if (event instanceof MouseEvent) {
            return ((MouseEvent)event).getClickCount() >= 1;
          }

          return false;
        }

        @Override
        protected boolean isApplicable(NotificationDisplayType value, int row) {
          if (value != NotificationDisplayType.TOOL_WINDOW) return true;

          String groupId = ((NotificationsTableModel)getModel()).getSettings(row).getGroupId();
          return NotificationsConfigurationImpl.getNotificationsConfigurationImpl().hasToolWindowCapability(groupId);
        }

        @Override
        protected String getTextFor(@NotNull NotificationDisplayType value) {
          return value.getTitle();
        }
      });

      final TableColumn logColumn = getColumnModel().getColumn(LOG_COLUMN);
      logColumn.setMaxWidth(logColumn.getPreferredWidth());
      if (SystemInfo.isMac) {
        final TableColumn readAloudColumn = getColumnModel().getColumn(READ_ALOUD_COLUMN);
        readAloudColumn.setMaxWidth(readAloudColumn.getPreferredWidth());
      }
      new TableSpeedSearch(this);
      getEmptyText().setText("No notifications configured");
    }

    @Override
    public Dimension getMinimumSize() {
      return calcSize(super.getMinimumSize());
    }

    @Override
    public Dimension getPreferredSize() {
      return calcSize(super.getPreferredSize());
    }

    private Dimension calcSize(@NotNull final Dimension s) {
      final Container container = getParent();
      if (container != null) {
        final Dimension size = container.getSize();
        return new Dimension(size.width, s.height);
      }

      return s;
    }

    public List<SettingsWrapper> getSettings() {
      return ((NotificationsTableModel)getModel()).getSettings();
    }

    public void removeSelected() {
      final ListSelectionModel selectionModel = getSelectionModel();
      if (!selectionModel.isSelectionEmpty()) {
        final int min = selectionModel.getMinSelectionIndex();
        final int max = selectionModel.getMaxSelectionIndex();

        final List<SettingsWrapper> settings = getSettings();
        final List<SettingsWrapper> toRemove = new ArrayList<SettingsWrapper>();

        for (int i = min; i <= max && i < settings.size(); i++) {
          toRemove.add(settings.get(i));
        }

        if (toRemove.size() > 0) {
          ((NotificationsTableModel)getModel()).remove(toRemove);

          revalidate();
          repaint();
        }
      }
    }

    public List<SettingsWrapper> getAllSettings() {
      return ((NotificationsTableModel)getModel()).getAllSettings();
    }
  }

  private static class SettingsWrapper {
    private boolean myRemoved = false;
    private NotificationSettings myVersion;

    private SettingsWrapper(NotificationSettings settings) {
      myVersion = settings;
    }

    public boolean hasChanged() {
      return myRemoved || !getOriginalSettings().equals(myVersion);
    }

    public void remove() {
      myRemoved = true;
    }

    public boolean isRemoved() {
      return myRemoved;
    }

    @NotNull
    private NotificationSettings getOriginalSettings() {
      return NotificationsConfigurationImpl.getSettings(getGroupId());
    }

    public void apply() {
      if (myRemoved) {
        NotificationsConfigurationImpl.remove(getGroupId());
      }
      else {
        NotificationsConfigurationImpl.getNotificationsConfigurationImpl().changeSettings(myVersion);
      }
    }

    public void reset() {
      myVersion = getOriginalSettings();
      myRemoved = false;
    }

    String getGroupId() {
      return myVersion.getGroupId();
    }
  }

  private static class NotificationsTableModel extends AbstractTableModel {
    private final List<SettingsWrapper> mySettings;

    public NotificationsTableModel() {
      final List<SettingsWrapper> list = new ArrayList<SettingsWrapper>();
      for (NotificationSettings setting : NotificationsConfigurationImpl.getAllSettings()) {
        list.add(new SettingsWrapper(setting));
      }

      mySettings = list;
    }

    public List<SettingsWrapper> getSettings() {
      return getActiveSettings();
    }

    @Override
    public void setValueAt(final Object value, final int rowIndex, final int columnIndex) {
      final SettingsWrapper wrapper = getSettings(rowIndex);

      switch (columnIndex) {
        case NotificationsTable.DISPLAY_TYPE_COLUMN:
          wrapper.myVersion = wrapper.myVersion.withDisplayType((NotificationDisplayType)value);
          break;
        case NotificationsTable.LOG_COLUMN:
          wrapper.myVersion = wrapper.myVersion.withShouldLog((Boolean)value);
          break;
        case NotificationsTable.READ_ALOUD_COLUMN:
          wrapper.myVersion = wrapper.myVersion.withShouldReadAloud((Boolean)value);
          break;
      }
    }

    public int getRowCount() {
      return getSettings().size();
    }

    public SettingsWrapper getSettings(int row) {
      return getSettings().get(row);
    }

    public int getColumnCount() {
      return SystemInfo.isMac ? 4 : 3;
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      if (NotificationsTable.DISPLAY_TYPE_COLUMN == columnIndex) {
        return NotificationDisplayType.class;
      }
      if (NotificationsTable.LOG_COLUMN == columnIndex) {
        return Boolean.class;
      }
      if (NotificationsTable.READ_ALOUD_COLUMN == columnIndex) {
        return Boolean.class;
      }

      return String.class;
    }

    @Override
    public String getColumnName(final int column) {
      switch (column) {
        case NotificationsTable.ID_COLUMN:
          return "Group";
        case NotificationsTable.LOG_COLUMN:
          return "Log";
        case NotificationsTable.READ_ALOUD_COLUMN:
          return "Read aloud";
        default:
          return "Popup";
      }
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      return columnIndex > 0;
    }

    public Object getValueAt(final int rowIndex, final int columnIndex) {
      switch (columnIndex) {
        case NotificationsTable.ID_COLUMN:
          return getSettings().get(rowIndex).getGroupId();
        case NotificationsTable.LOG_COLUMN:
          return getSettings().get(rowIndex).myVersion.isShouldLog();
        case NotificationsTable.READ_ALOUD_COLUMN:
          return getSettings().get(rowIndex).myVersion.isShouldReadAloud();
        case NotificationsTable.DISPLAY_TYPE_COLUMN:
        default:
          return getSettings().get(rowIndex).myVersion.getDisplayType();
      }
    }

    public static void remove(List<SettingsWrapper> toRemove) {
      for (SettingsWrapper settingsWrapper : toRemove) {
        settingsWrapper.remove();
      }
    }

    public List<SettingsWrapper> getAllSettings() {
      return mySettings;
    }

    private List<SettingsWrapper> getActiveSettings() {
      final List<SettingsWrapper> result = new ArrayList<SettingsWrapper>(mySettings);
      final Iterator<SettingsWrapper> iterator = result.iterator();
      while (iterator.hasNext()) {
        final SettingsWrapper wrapper = iterator.next();
        if (wrapper.isRemoved()) {
          iterator.remove();
        }
      }

      return result;
    }
  }
}
