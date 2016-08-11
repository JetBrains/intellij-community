/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.EditorSettingsProvider;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;
import java.util.List;

import static java.awt.event.KeyEvent.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBListTable {
  protected final JTable myInternalTable;
  private final JBTable myMainTable;
  private final RowResizeAnimator myRowResizeAnimator;
  protected MouseEvent myMouseEvent;
  private MyCellEditor myCellEditor;
  private int myLastFocusedEditorComponentIdx = -1;

  public JBListTable(@NotNull final JTable t, @NotNull Disposable parent) {
    myInternalTable = t;
    myMainTable = new MyTable();
    myMainTable.setTableHeader(null);
    myMainTable.setStriped(true);
    myRowResizeAnimator = new RowResizeAnimator(myMainTable);
    Disposer.register(parent, myRowResizeAnimator);
  }

  public void stopEditing() {
    TableUtil.stopEditing(myMainTable);
  }

  private static void installPaddingAndBordersForEditors(JBTableRowEditor editor) {
    final List<EditorTextField> editors = UIUtil.findComponentsOfType(editor, EditorTextField.class);
    for (EditorTextField textField : editors) {
      textField.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
      textField.putClientProperty("JBListTable.isTableCellEditor", Boolean.TRUE);
    }
  }

  public final JBTable getTable() {
    return myMainTable;
  }

  protected abstract JBTableRowRenderer getRowRenderer(int row);

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

  private class MyCellEditor extends AbstractTableCellEditor {
    private final JBTableRowEditor myEditor;

    public MyCellEditor(JBTableRowEditor editor) {
      myEditor = editor;
    }

    @Override
    public boolean isCellEditable(EventObject e) {
      if (e instanceof MouseEvent && UIUtil.isSelectionButtonDown((MouseEvent)e)) {
        return false;
      }
      return super.isCellEditable(e);
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, final int row, int column) {
      final JPanel p = new JPanel(new BorderLayout()) {
        @Override
        public void addNotify() {
          super.addNotify();
          int height = getPreferredSize().height;
          if (height > table.getRowHeight(row)) {
            myRowResizeAnimator.resize(row, height);
          }
        }

        public void removeNotify() {
          if (myCellEditor != null) myCellEditor.saveFocusIndex();
          super.removeNotify();
          myRowResizeAnimator.resize(row, table.getRowHeight());
        }
      };
      p.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(p);
          focusManager.requestFocus(getComponentToFocus(), true);
        }

        private Component getComponentToFocus() {
          if (myLastFocusedEditorComponentIdx >= 0) {
            JComponent[] focusableComponents = myEditor.getFocusableComponents();
            if (myLastFocusedEditorComponentIdx < focusableComponents.length) {
              return focusableComponents[myLastFocusedEditorComponentIdx];
            }
          }
          return myEditor.getPreferredFocusedComponent();
        }
      });
      p.add(myEditor, BorderLayout.CENTER);
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

    @Override
    public void cancelCellEditing() {
      saveFocusIndex();
      super.cancelCellEditing();
    }

    private void saveFocusIndex() {
      JComponent[] components = myEditor.getFocusableComponents();
      for (int i = 0; i < components.length; i++) {
        if (components[i].hasFocus()) {
          myLastFocusedEditorComponentIdx = i;
          break;
        }
      }
    }
  }

  private static class RowResizeAnimator implements ActionListener, Disposable {
    private static final int ANIMATION_STEP_MILLIS = 15;
    private static final int RESIZE_AMOUNT_PER_STEP = 5;

    private final TIntObjectHashMap<RowAnimationState> myRowAnimationStates = new TIntObjectHashMap<>();
    private final Timer myAnimationTimer = UIUtil.createNamedTimer("JBListTableTimer",ANIMATION_STEP_MILLIS, this);
    private final JTable myTable;

    public RowResizeAnimator(JTable table) {
      myTable = table;
    }

    public void resize(int row, int targetHeight) {
      myRowAnimationStates.put(row, new RowAnimationState(row, targetHeight));
      startAnimation();
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
      doAnimationStep(e.getWhen());
    }

    @Override
    public void dispose() {
      stopAnimation();
      // enforce all animations are completed
      doAnimationStep(Long.MAX_VALUE);
    }

    private void startAnimation() {
      if (!myAnimationTimer.isRunning()) {
        myAnimationTimer.start();
      }
    }

    private void stopAnimation() {
      myAnimationTimer.stop();
    }

    private void doAnimationStep(final long updateTime) {
      final TIntArrayList completeRows = new TIntArrayList(myRowAnimationStates.size());
      myRowAnimationStates.forEachEntry(new TIntObjectProcedure<RowAnimationState>() {
        @Override
        public boolean execute(int row, RowAnimationState animationState) {
          if (animationState.doAnimationStep(updateTime)) {
            completeRows.add(row);
          }
          return true;
        }
      });
      completeRows.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int row) {
          myRowAnimationStates.remove(row);
          return true;
        }
      });
      if (myRowAnimationStates.isEmpty()) {
        stopAnimation();
      }
    }

    private class RowAnimationState {
      private final int myRow;
      private final int myTargetHeight;
      private long myLastUpdateTime;

      public RowAnimationState(int row, int targetHeight) {
        myRow = row;
        myTargetHeight = targetHeight;
        myLastUpdateTime = System.currentTimeMillis();
      }

      /**
       * @return whether this row animation is complete
       */
      public boolean doAnimationStep(long currentTime) {
        if (myRow >= myTable.getRowCount()) return true;

        int currentRowHeight = myTable.getRowHeight(myRow);
        int resizeAbs = (int) (RESIZE_AMOUNT_PER_STEP * ((currentTime - myLastUpdateTime) / (double)ANIMATION_STEP_MILLIS));
        int leftToAnimate = myTargetHeight - currentRowHeight;
        int newHeight = Math.abs(leftToAnimate) <= resizeAbs ? myTargetHeight :
                        currentRowHeight + (leftToAnimate < 0 ? -resizeAbs : resizeAbs);
        myTable.setRowHeight(myRow, newHeight);
        myLastUpdateTime = currentTime;
        return myTargetHeight == newHeight;
      }
    }
  }

  private class MyTable extends JBTable {

    public MyTable() {
      super(new MyTableModel(myInternalTable.getModel()));
    }

    @Override
    public MyTableModel getModel() {
      return (MyTableModel)super.getModel();
    }

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
      final JBTableRowRenderer rowRenderer = getRowRenderer(row);
      return new TableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int col) {
          return rowRenderer.getRowRendererComponent(myInternalTable, row, selected, focused);
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
          MyTableModel model = getModel();
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
    public void columnMarginChanged(ChangeEvent e) {
      // we don't stop editing (it prevents editor removal when scrollbar is added)
      TableColumn resizingColumn = tableHeader != null ? tableHeader.getResizingColumn() : null;
      if (resizingColumn != null && autoResizeMode == AUTO_RESIZE_OFF) {
        resizingColumn.setPreferredWidth(resizingColumn.getWidth());
      }
      resizeAndRepaint();
    }

    @Override
    public TableCellEditor getCellEditor(final int row, int column) {
      final JBTableRowEditor editor = getRowEditor(row);
      if (editor != null) {
        editor.setMouseEvent(myMouseEvent);
        editor.prepareEditor(myInternalTable, row);
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

    @Override
    public void addNotify() {
      super.addNotify();
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      Disposer.dispose(myRowResizeAnimator);
    }
  }

  private class MyTableModel extends JBListTableModel {

    MyTableModel(TableModel model) {
      super(model);
    }

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
      myLastFocusedEditorComponentIdx = -1;
      super.addRow();
    }
  }
}
