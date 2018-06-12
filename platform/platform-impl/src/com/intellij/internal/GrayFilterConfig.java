// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.JBColor;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.getGrayFilterProperty;
import static com.intellij.util.ui.UIUtil.setGrayFilterProperty;

/**
 * @author tav
 */
public class GrayFilterConfig extends AnAction implements DumbAware {
  private static final String BRIGHTNESS = "brightness";
  private static final String CONTRAST = "contrast";
  private static final String ALPHA = "alpha";

  @SuppressWarnings("MismatchedReadAndWriteOfArray")
  private final Object[][] data = new Object[3][2];

  private void setData() {
    data[0][0] = BRIGHTNESS;
    data[1][0] = CONTRAST;
    data[2][0] = ALPHA;
    data[0][1] = String.valueOf(getGrayFilterProperty(BRIGHTNESS));
    data[1][1] = String.valueOf(getGrayFilterProperty(CONTRAST));
    data[2][1] = String.valueOf(getGrayFilterProperty(ALPHA));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    setData();
    LafManager.getInstance().addLafManagerListener(source -> setData());

    JTable table = new JTable(data, new String[] {"Property", "Value"}) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return (table1, value, isSelected, hasFocus, row1, column1) -> {
          JLabel label = new JLabel((String)data[row][column]);
          label.setOpaque(true);
          label.setBackground(JBColor.border());
          label.setBorder(JBUI.Borders.emptyLeft(UISettings.getDefFontSize()));
          return label;
        };
      }
      @Override
      public boolean isCellEditable(int row, int column) {
        return column == 1;
      }
    };

    table.getColumnModel().getColumn(1).setCellEditor(new AbstractTableCellEditor() {
      JTextField component = new JTextField();

      @Override
      public Object getCellEditorValue() {
        return component.getText();
      }

      @Override
      public Component getTableCellEditorComponent(JTable table1, Object value, boolean isSelected, int row, int column) {
        component.setText((String)data[row][column]);
        return component;
      }
    });
    table.getColumnModel().getColumn(1).getCellEditor().addCellEditorListener(new CellEditorListener() {
      @Override
      public void editingStopped(ChangeEvent e) {
        String prop = (String)data[table.getSelectedRow()][0];
        try {
          int value = Integer.parseInt((String)((TableCellEditor)e.getSource()).getCellEditorValue());

          setGrayFilterProperty(prop, value);
          data[table.getSelectedRow()][1] = String.valueOf(getGrayFilterProperty(prop));

          IconLoader.clearCache();
          updateUI(SwingUtilities.getWindowAncestor(table));
        } catch (Throwable ignored) {
        }
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
      }
    });

    table.setColumnSelectionAllowed(true);
    table.setTableHeader(null);

    for (int c=0; c<table.getColumnCount(); c++)
      table.getColumnModel().getColumn(c).setPreferredWidth(JBUI.scale(100));
    for (int r=0; r<table.getRowCount(); r++)
      table.setRowHeight(r, UISettings.getDefFontSize() * 2);

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(table, BorderLayout.CENTER);

    DialogWrapper dlg = new DialogWrapper(false) {
      {
        init();
      }
      @Override
      protected JComponent createCenterPanel() {
        return tablePanel;
      }
    };
    dlg.setModal(false);
    dlg.setTitle("GrayFilter");
    dlg.setResizable(false);
    dlg.show();
  }

  private static void updateUI(Window exclude) {
    for (Window w : Window.getWindows()) {
      if (w == exclude) continue;
      IJSwingUtilities.updateComponentTreeUI(w);
    }
  }
}
