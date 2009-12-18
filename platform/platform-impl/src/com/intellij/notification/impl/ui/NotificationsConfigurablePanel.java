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
import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.StripeTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
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

  public NotificationsConfigurablePanel() {
    setLayout(new BorderLayout());
    myTable = new NotificationsTable();

    add(StripeTable.createScrollPane(myTable), BorderLayout.CENTER);

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

    return false;
  }

  public void apply() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.apply();
    }
  }

  public void reset() {
    final List<SettingsWrapper> list = myTable.getAllSettings();
    for (SettingsWrapper settingsWrapper : list) {
      settingsWrapper.reset();
    }

    myTable.invalidate();
    myTable.repaint();
  }

  private static class NotificationsTable extends StripeTable {
    private static final int ID_COLUMN = 0;
    private static final int DISPLAY_TYPE_COLUMN = 1;

    public NotificationsTable() {
      super(new NotificationsTableModel());

      final TableColumn idColumn = getColumnModel().getColumn(ID_COLUMN);
      idColumn.setPreferredWidth(200);

      final TableColumn displayTypeColumn = getColumnModel().getColumn(DISPLAY_TYPE_COLUMN);
      displayTypeColumn.setMaxWidth(300);
      displayTypeColumn.setPreferredWidth(250);
      displayTypeColumn.setCellRenderer(new ComboBoxTableRenderer<NotificationDisplayType>(NotificationDisplayType.values()) {
        @Override
        protected String getTextFor(@NotNull NotificationDisplayType value) {
          return value.getTitle();
        }
      });

      displayTypeColumn.setCellEditor(new ComboBoxTableRenderer<NotificationDisplayType>(NotificationDisplayType.values()) {
        @Override
        public boolean isCellEditable(EventObject event) {
          if (event instanceof MouseEvent) {
              return ((MouseEvent)event).getClickCount() >= 1;
          }

          return false;
        }

        @Override
        protected String getTextFor(@NotNull NotificationDisplayType value) {
          return value.getTitle();
        }
      });
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

  private static class SettingsWrapper extends NotificationSettings {
    private NotificationSettings myOriginal;
    private boolean myRemoved = false;

    private SettingsWrapper(@NotNull final NotificationSettings original) {
      super(original.getGroupId(), original.getDisplayType());
      myOriginal = original;
    }

    public boolean hasChanged() {
      return !getDisplayType().equals(myOriginal.getDisplayType()) || myRemoved;
    }

    public void remove() {
      myRemoved = true;
    }

    public boolean isRemoved() {
      return myRemoved;
    }

    public void apply() {
      if (myRemoved) {
        NotificationsConfiguration.remove(new NotificationSettings[]{myOriginal});
      }
      else {
        if (hasChanged()) {
          myOriginal.setDisplayType(getDisplayType());
        }
      }
    }

    public void reset() {
      if (hasChanged()) {
        setDisplayType(myOriginal.getDisplayType());
        myRemoved = false;
      }
    }
  }

  private static class NotificationsTableModel extends AbstractTableModel {
    private List<SettingsWrapper> mySettings;

    public NotificationsTableModel() {
      final NotificationSettings[] settings = NotificationsConfiguration.getAllSettings();
      final List<SettingsWrapper> list = new ArrayList<SettingsWrapper>();
      for (NotificationSettings setting : settings) {
        list.add(new SettingsWrapper(setting));
      }

      mySettings = list;
    }

    public List<SettingsWrapper> getSettings() {
      return getActiveSettings();
    }

    @Override
    public void setValueAt(final Object value, final int rowIndex, final int columnIndex) {
      final NotificationSettings settings = getSettings(rowIndex);

      switch (columnIndex) {
        case NotificationsTable.DISPLAY_TYPE_COLUMN:
          settings.setDisplayType((NotificationDisplayType)value);
          break;
      }
    }

    public int getRowCount() {
      return getSettings().size();
    }

    public NotificationSettings getSettings(int row) {
      return getSettings().get(row);
    }

    public int getColumnCount() {
      return 2;
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      if (NotificationsTable.DISPLAY_TYPE_COLUMN == columnIndex) {
        return NotificationDisplayType.class;
      }

      return String.class;
    }

    @Override
    public String getColumnName(final int column) {
      switch (column) {
        case NotificationsTable.ID_COLUMN:
          return "Group";
        default:
          return "Display";
      }
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      return columnIndex == NotificationsTable.DISPLAY_TYPE_COLUMN;
    }

    public Object getValueAt(final int rowIndex, final int columnIndex) {
      switch (columnIndex) {
        case NotificationsTable.ID_COLUMN:
          return getSettings().get(rowIndex).getGroupId();
        case NotificationsTable.DISPLAY_TYPE_COLUMN:
        default:
          return getSettings().get(rowIndex).getDisplayType();
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
