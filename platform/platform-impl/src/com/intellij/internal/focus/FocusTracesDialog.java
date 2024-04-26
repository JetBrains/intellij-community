// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.focus;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.FocusRequestInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class FocusTracesDialog extends DialogWrapper {
  private final JBTable myRequestsTable;
  private final List<FocusRequestInfo> myRequests;
  private static final String[] COLUMNS = {"Time", "Forced", "Component"};
  private final ConsoleView consoleView;

  public FocusTracesDialog(Project project, ArrayList<FocusRequestInfo> requests) {
    super(project);
    myRequests = requests;
    setTitle("Focus Traces");
    final String[][] data = new String[requests.size()][];
    SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    for (int i = 0; i < data.length; i++) {
      final FocusRequestInfo r = requests.get(i);
      data[i] = new String[]{
        dateFormat.format(new Date(r.timestamp)),
        String.valueOf(r.forced),
        r.componentString + (!"null".equals(r.componentString) &&
                             r.component.get() == null ? " <collected>" : "")};
    }
    setModal(false);
    myRequestsTable = new JBTable(new DefaultTableModel(data, COLUMNS) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    });
    final ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (consoleView == null) return;
        final int index = myRequestsTable.getSelectedRow();
        consoleView.clear();
        if (-1 < index && index < myRequests.size()) {
          consoleView.print(ExceptionUtil.getThrowableText(myRequests.get(index).trace), ConsoleViewContentType.NORMAL_OUTPUT);
        }
      }
    };
    myRequestsTable.getSelectionModel().addListSelectionListener(selectionListener);
    final TableColumnModel columnModel = myRequestsTable.getColumnModel();
    columnModel.getColumn(0).setMinWidth(JBUIScale.scale(120));
    columnModel.getColumn(0).setMaxWidth(JBUIScale.scale(120));
    columnModel.getColumn(0).setPreferredWidth(JBUIScale.scale(120));
    columnModel.getColumn(1).setMinWidth(JBUIScale.scale(60));
    columnModel.getColumn(1).setMaxWidth(JBUIScale.scale(60));
    columnModel.getColumn(1).setPreferredWidth(JBUIScale.scale(60));
    myRequestsTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myRequestsTable.changeSelection(0, 0, false, true);

    consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    Disposer.register(getDisposable(), consoleView);

    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "ide.internal.focus.trace.dialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JBSplitter splitter = new JBSplitter(true, .5F, .2F, .8F);
    splitter.setFirstComponent(new JBScrollPane(myRequestsTable, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));

    JComponent consoleComponent = new JPanel(new BorderLayout());
    consoleComponent.add(consoleView.getComponent(), BorderLayout.CENTER);
    int row = myRequestsTable.getSelectedRow();
    if (row >= 0) {
      consoleView.print(ExceptionUtil.getThrowableText(myRequests.get(row).trace), ConsoleViewContentType.NORMAL_OUTPUT);
    }

    splitter.setSecondComponent(
      new JBScrollPane(consoleComponent, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
    panel.add(splitter, BorderLayout.CENTER);
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRequestsTable;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[] {getOKAction(), getCopyStackTraceAction()};
  }

  private Action getCopyStackTraceAction() {
    return new AbstractAction("&Copy stacktrace") {
      @Override
      public void actionPerformed(ActionEvent e) {
        String text = ExceptionUtil.getThrowableText(myRequests.get(myRequestsTable.getSelectedRow()).trace);
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
      }
    };
  }

  @Override
  public void show() {
    final BorderDrawer drawer = new BorderDrawer();
    drawer.start();
    super.show();
    drawer.setDone();
  }

  final class BorderDrawer extends Thread {
    Component prev = null;
    private volatile boolean running = true;
    BorderDrawer() {
      super("Focus Border Drawer");
    }

    @Override
    public void run() {
      try {
        while (running) {
          //noinspection BusyWait
          sleep(100);
          paintBorder();
        }
        if (prev != null) {
          prev.repaint();
        }
      }
      catch (InterruptedException ignored) {
      }
    }

    private void paintBorder() {
      final int row = FocusTracesDialog.this.myRequestsTable.getSelectedRow();
      if (row != -1) {
        final FocusRequestInfo info = FocusTracesDialog.this.myRequests.get(row);
        if (prev != null && prev != info.component.get()) {
          prev.repaint();
        }
        prev = info.component.get();
        if (prev != null && prev.isDisplayable()) {
          final Graphics g = prev.getGraphics();
          g.setColor(JBColor.RED);
          final Dimension sz = prev.getSize();
          UIUtil.drawDottedRectangle(g, 1, 1, sz.width - 2, sz.height - 2);
        }
      }
    }

    public void setDone() {
      running = false;
    }
  }
}
