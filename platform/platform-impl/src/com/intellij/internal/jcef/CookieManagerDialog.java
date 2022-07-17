// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.
package com.intellij.internal.jcef;

import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefCookie;
import com.intellij.ui.jcef.JBCefCookieManager;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

class CookieManagerDialog extends JDialog {
  private static final String myTitle = "Cookie Manager";
  private static final String myDeleteCookiesButtonText = "Delete All Cookies";
  @SuppressWarnings("unused") private final JBCefBrowser myJBCefBrowser;
  private final JBCefCookieManager myJBCefCookieManager;
  private final CookieTableModel myTableModel = new CookieTableModel();

  protected CookieManagerDialog(Frame owner, JBCefBrowser jbCefBrowser) {
    super(owner, myTitle, false);
    setLayout(new BorderLayout());
    setSize(JBDimension.size(new Dimension(800, 600)));

    myJBCefBrowser = jbCefBrowser;
    myJBCefCookieManager = jbCefBrowser.getJBCefCookieManager();
    JTable cookieTable = new JBTable(myTableModel);
    cookieTable.setFillsViewportHeight(true);
    add(new JScrollPane(cookieTable));

    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));

    final JButton myDeleteCookiesButton = new JButton(myDeleteCookiesButtonText);
    myDeleteCookiesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myJBCefCookieManager.deleteCookies(true)) {
          List<JBCefCookie> cookies = myJBCefCookieManager.getCookies();
          if (cookies != null) {
            update(cookies);
          }
        }
      }
    });
    controlPanel.add(myDeleteCookiesButton);

    add(controlPanel, BorderLayout.SOUTH);
  }

  public void update(List<? extends JBCefCookie> cefCookies) {
    myTableModel.clear();
    myTableModel.show(cefCookies);
  }

  private static class CookieTableModel extends AbstractTableModel {
    private final String[] columnNames =
      new String[]{"Name", "Value", "Domain", "Path", "Secure", "HTTP only", "Created", "Last Access", "Expires"};
    private final ArrayList<Object[]> rowData = new ArrayList<>();

    private void show(@NotNull List<? extends JBCefCookie> cefCookies) {
      for (JBCefCookie cookie : cefCookies) {
        Object[] entry = {
          cookie.getName(),
          cookie.getValue(),
          cookie.getDomain(),
          cookie.getPath(),
          cookie.isSecure(),
          cookie.isHttpOnly(),
          cookie.getCreation(),
          cookie.getLastAccess(),
          cookie.getExpires()
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
