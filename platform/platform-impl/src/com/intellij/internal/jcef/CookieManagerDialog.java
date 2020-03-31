// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.intellij.internal.jcef;

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.cef.network.CefCookie;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


class CookieManagerDialog extends JDialog {
  private final CookieTableModel myTableModel = new CookieTableModel();
  private static final String myTitle = "Cookie Manager";

  protected CookieManagerDialog(Frame owner) {
    super(owner, myTitle, false);
    setLayout(new BorderLayout());
    setSize(JBUI.size(new Dimension(800, 600)));

    JTable cookieTable = new JBTable(myTableModel);
    cookieTable.setFillsViewportHeight(true);
    add(new JScrollPane(cookieTable));
  }

  public void update(List<CefCookie> cefCookies) {
    myTableModel.clear();
    myTableModel.show(cefCookies);
  }

  private class CookieTableModel extends AbstractTableModel {
    private final String[] columnNames =
      new String[]{"Name", "Value", "Domain", "Path", "Secure", "HTTP only", "Created", "Last Access", "Expires"};
    private ArrayList<Object[]> rowData = new ArrayList<>();

    private void show(@NotNull List<CefCookie> cefCookies) {
      for (CefCookie cookie : cefCookies) {
        Object[] entry = {
          cookie.name,
          cookie.value,
          cookie.domain,
          cookie.path,
          Boolean.valueOf(cookie.secure),
          Boolean.valueOf(cookie.httponly),
          cookie.creation,
          cookie.lastAccess,
          cookie.expires
        };
        int row = rowData.size();
        rowData.add(entry);
        fireTableRowsInserted(row, row);
      }
    }

    public void clear() {
      int count = rowData.size();
      if (count > 0) {
        rowData.clear();
        fireTableRowsDeleted(0, count - 1);
      }
    }

    @Override
    public int getRowCount() {
      return rowData.size();
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (rowData.size() > 0) {
        if (rowData.get(0)[columnIndex] != null) {
          return rowData.get(0)[columnIndex].getClass();
        }
      }

      return Object.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return rowData.get(rowIndex)[columnIndex];
    }
  }
}
