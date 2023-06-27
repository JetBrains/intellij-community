// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTableUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public final class BegTableUI extends BasicTableUI {
  private final KeyAdapter myAdapter= new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          if (table.isEditing()) {
            e.consume();
            table.removeEditor();

            if (e.getSource() != table) {
              ((JComponent)e.getSource()).removeKeyListener(this);
            }
          }
        }
      }
    };
  @NonNls public static final String START_EDITING_ACTION_KEY = "startEditing";

  public static ComponentUI createUI(JComponent c) {
    return new BegTableUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    c.getActionMap().put(START_EDITING_ACTION_KEY, new StartEditingAction());
    // fix missing escape shortcut
    c.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("pressed ESCAPE"), "cancel");
  }

  @Override
  public void installDefaults() {
    super.installDefaults();

    int rowHeight = UIManager.getInt("Table.rowHeight");
    if (rowHeight > 0) {
      LookAndFeel.installProperty(table, "rowHeight", rowHeight);
    }
  }

  @Override
  protected KeyListener createKeyListener() {
    return myAdapter;
  }

  private class StartEditingAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      JTable table = (JTable)e.getSource();
      if (!table.hasFocus()) {
        CellEditor cellEditor = table.getCellEditor();
        if (cellEditor != null && !cellEditor.stopCellEditing()) {
          return;
        }
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
        return;
      }
      ListSelectionModel rsm = table.getSelectionModel();
      int anchorRow = rsm.getAnchorSelectionIndex();
      ListSelectionModel csm = table.getColumnModel().getSelectionModel();
      int anchorColumn = csm.getAnchorSelectionIndex();
      table.editCellAt(anchorRow, anchorColumn, e);
      Component editorComp = table.getEditorComponent();
      if (editorComp != null) {
        editorComp.addKeyListener(myAdapter);
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(editorComp, true));
      }
    }
  }

}
