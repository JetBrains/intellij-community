/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import static java.awt.event.KeyEvent.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBListTable extends JPanel {
  protected final JTable myInternalTable;
  private final JBTable mainTable;
  private final Ref<Integer> myLastEditorIndex = Ref.create(null);
  private MouseEvent myMouseEvent;
  private MyCellEditor myCellEditor;

  public JBListTable(@NotNull final JTable t) {
    super(new BorderLayout());
    myInternalTable = t;
    final JBListTableModel model = new JBListTableModel(t.getModel()) {
      @Override
      public JBTableRow getRow(int index) {
        return getRowAt(index);
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return isRowEditable(rowIndex);
      }

      @Override
      public void addRow() {
        myLastEditorIndex.set(null);
        super.addRow();
      }
    };
    mainTable = new JBTable(model) {
      @Override
      public void editingStopped(ChangeEvent e) {
        super.editingStopped(e);
      }

      @Override
      public void editingCanceled(ChangeEvent e) {
        super.editingCanceled(e);
      }

      @Override
      protected void processKeyEvent(KeyEvent e) {
        myMouseEvent = null;
        
        //Mnemonics
        if (e.isAltDown()) {
          super.processKeyEvent(e);
          return;
        }

        if (e.getKeyCode() == VK_TAB) {
          if (e.getID() == KEY_PRESSED) {
            final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            if (e.isShiftDown()) {
              keyboardFocusManager.focusPreviousComponent(this);
            }
            else {
              keyboardFocusManager.focusNextComponent(this);
            }
          }
          e.consume();
          return;
        }

        super.processKeyEvent(e);
      }

      @Override
      protected void processMouseEvent(MouseEvent e) {
        myMouseEvent = e;
        super.processMouseEvent(e);
      }

      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean hasFocus, int row, int col) {
            return getRowRenderer(t, row, selected, hasFocus);
          }
        };
      }

      @Override
      protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        //Mnemonics and actions
        if (e.isAltDown() || e.isMetaDown() || e.isControlDown()) {
          return false;
        }
        
        if (e.getKeyCode() == VK_ESCAPE && pressed) {
          final int row = getSelectedRow();
          if (row != -1 && isRowEmpty(row)) {
            final int count = model.getRowCount();
            model.removeRow(row);
            int newRow = count == row + 1 ? row - 1 : row;

            if (0 <= newRow && newRow < model.getRowCount()) {
              setRowSelectionInterval(newRow, newRow);
            }

          }
        }

        if (e.getKeyCode() == VK_ENTER) {
          if (e.getID() == KEY_PRESSED) {
            if (!isEditing() && e.getModifiers() == 0) {
              editCellAt(getSelectedRow(), getSelectedColumn());
            }
            else if (isEditing()) {
              TableUtil.stopEditing(this);
              if (e.isControlDown() || e.isMetaDown()) {
                return false;
              }
              else {
                final int row = getSelectedRow() + 1;
                if (row < getRowCount()) {
                  getSelectionModel().setSelectionInterval(row, row);
                }
              }
            }
            else {
              if (e.isControlDown() || e.isMetaDown()) {
                return false;
              }
            }
          }
          e.consume();
          return true;
        }

        if (isEditing() && e.getKeyCode() == VK_TAB) {
          if (pressed) {
            final KeyboardFocusManager mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            if (e.isShiftDown()) {
              mgr.focusPreviousComponent();
            }
            else {
              mgr.focusNextComponent();
            }
          }
          return true;
        }

        final boolean isUp = e.getKeyCode() == VK_UP;
        final boolean isDown = e.getKeyCode() == VK_DOWN;

        if (isEditing() && (isUp || isDown) && e.getModifiers() == 0 && e.getID() == KEY_PRESSED) {
          int row = getSelectedRow();
          super.processKeyBinding(ks, e, condition, pressed);
          if (!isEditing() && row != getSelectedRow()) {
            TableUtil.editCellAt(this, getSelectedRow(), 0);
            e.consume();
            return true;
          }
        }

        return super.processKeyBinding(ks, e, condition, pressed);
      }

      @Override
      public TableCellEditor getCellEditor(final int row, int column) {
        final JBTableRowEditor editor = getRowEditor(row);
        if (editor != null) {
          editor.setMouseEvent(myMouseEvent);
          editor.prepareEditor(t, row);
          installPaddingAndBordersForEditors(editor);
          editor.setFocusCycleRoot(true);

          editor.setFocusTraversalPolicy(new JBListTableFocusTraversalPolicy(editor));
          MouseSuppressor.install(editor);

          myCellEditor = new MyCellEditor(editor);
          return myCellEditor;
        }
        myCellEditor = null;
        return myCellEditor;
      }

      @Override
      public Component prepareEditor(TableCellEditor editor, int row, int column) {
        Object value = getValueAt(row, column);
        boolean isSelected = isCellSelected(row, column);
        return editor.getTableCellEditorComponent(this, value, isSelected, row, column);
      }
    };
    mainTable.setStriped(true);
  }

  public void stopEditing() {
    TableUtil.stopEditing(mainTable);
  }

  private static void installPaddingAndBordersForEditors(JBTableRowEditor editor) {
    final List<EditorTextField> editors = UIUtil.findComponentsOfType(editor, EditorTextField.class);
    for (EditorTextField textField : editors) {
      textField.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
      textField.putClientProperty("JBListTable.isTableCellEditor", Boolean.TRUE);
    }
  }

  public final JBTable getTable() {
    return mainTable;
  }

  protected abstract JComponent getRowRenderer(JTable table, int row, boolean selected, boolean focused);

  protected abstract JBTableRowEditor getRowEditor(int row);

  protected JBTableRow getRowAt(final int row) {
    return new JBTableRow() {
      @Override
      public Object getValueAt(int column) {
        return myInternalTable.getValueAt(row, column);
      }
    };
  }

  protected boolean isRowEditable(int row) {
    return true;
  }
  
  protected boolean isRowEmpty(int row) {
    return false;
  }

  public static JComponent createEditorTextFieldPresentation(final Project project,
                                                             final FileType type,
                                                             final String text,
                                                             boolean selected,
                                                             boolean focused) {
    final JPanel panel = new JPanel(new BorderLayout());
    final EditorTextField field = new EditorTextField(text, project, type) {
      @Override
      protected boolean shouldHaveBorder() {
        return false;
      }
    };

    Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
    font = new Font(font.getFontName(), font.getStyle(), 12);
    field.setFont(font);
    field.addSettingsProvider(EditorSettingsProvider.NO_WHITESPACE);

    if (selected && focused) {
      panel.setBackground(UIUtil.getTableSelectionBackground());
      field.setAsRendererWithSelection(UIUtil.getTableSelectionBackground(), UIUtil.getTableSelectionForeground());
    } else {
      panel.setBackground(UIUtil.getTableBackground());
      if (selected) {
        panel.setBorder(new DottedBorder(UIUtil.getTableForeground()));
      }
    }
    panel.add(field, BorderLayout.WEST);
    return panel;
  }

  private static class RowResizeAnimator extends Thread {
    private final JTable myTable;
    private final int myRow;
    private final JScrollPane myScrollPane;
    private int neededHeight;
    private final JBTableRowEditor myEditor;
    private final Ref<Integer> myIndex;
    private int step = 5;
    private int currentHeight;

    private RowResizeAnimator(JTable table, int row, int height, JBTableRowEditor editor, @NotNull Ref<Integer> index) {
      super("Row Animator");
      myTable = table;
      myRow = row;
      neededHeight = height;
      myEditor = editor;
      myIndex = index;
      currentHeight = myTable.getRowHeight(myRow);
      myScrollPane = (JScrollPane)myTable.getParent().getParent();
    }

    @Override
    public void run() {
      final boolean exitEditing = currentHeight > neededHeight;
      try {
        sleep(50);
        final JScrollBar bar = myScrollPane.getVerticalScrollBar();
        if (bar == null || !bar.isVisible()) {
          myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        }
        while (currentHeight != neededHeight) {
          if (Math.abs(currentHeight - neededHeight) < step) {
            currentHeight = neededHeight;
          }
          else {
            currentHeight += currentHeight < neededHeight ? step : -step;
          }
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myTable.setRowHeight(myRow, currentHeight);
            }
          });
          sleep(15);
        }
        if (myEditor != null) {
          JComponent[] components = myEditor.getFocusableComponents();
          JComponent focus = null;
          if (myIndex.get() != null) {
            int index = myIndex.get().intValue();
            if (0 <= index && index < components.length) {
              focus = components[index];
            }
          }
          if (focus == null) {
            focus = myEditor.getPreferredFocusedComponent();
          }
          if (focus != null) {
            focus.requestFocus();
          }
        }
      }
      catch (InterruptedException ignore) {
      } finally {
        TableUtil.scrollSelectionToVisible(myTable);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (exitEditing && !myTable.isEditing()) {
              myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            }
          }
        });
      }
    }
  }

  private class MyCellEditor extends AbstractTableCellEditor implements Animated {
    JTable curTable;
    private final JBTableRowEditor myEditor;

    public MyCellEditor(JBTableRowEditor editor) {
      myEditor = editor;
      curTable = null;
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, final int row, int column) {
      curTable = table;
      final JPanel p = new JPanel(new BorderLayout()) {
        @Override
        public void addNotify() {
          super.addNotify();
          final int height = (int)getPreferredSize().getHeight();
          if (height > table.getRowHeight(row)) {
            new RowResizeAnimator(table, row, height, myEditor, myMouseEvent == null ? myLastEditorIndex : Ref.<Integer>create(null)).start();
          }
        }

        public void removeNotify() {
          if (myCellEditor != null) myCellEditor.saveFocusIndex();
          super.removeNotify();
          new RowResizeAnimator(table, row, table.getRowHeight(), null, myMouseEvent == null ? myLastEditorIndex : Ref.<Integer>create(null)).start();
        }
      };
      p.add(myEditor, BorderLayout.CENTER);
      p.setFocusable(false);
      return p;
    }

    @Override
    public Object getCellEditorValue() {
      return myEditor.getValue();
    }

    @Override
    public boolean stopCellEditing() {
      saveFocusIndex();
      return super.stopCellEditing();
    }

    private void removeEmptyRow() {
      final int row = curTable.getSelectedRow();
      if (row != -1 && isRowEmpty(row)) {
        final JBListTableModel model = (JBListTableModel)curTable.getModel();
        final int count = model.getRowCount();
        model.removeRow(row);
        int newRow = count == row + 1 ? row - 1 : row;
        curTable.setRowSelectionInterval(newRow, newRow);
      }
    }

    public void saveFocusIndex() {
      JComponent[] components = myEditor.getFocusableComponents();
      for (int i = 0; i < components.length; i++) {
        if (components[i].hasFocus()) {
          JBListTable.this.myLastEditorIndex.set(i);
          break;
        }
      }
    }

    @Override
    public void cancelCellEditing() {
      saveFocusIndex();
      super.cancelCellEditing();
    }
  }
}
