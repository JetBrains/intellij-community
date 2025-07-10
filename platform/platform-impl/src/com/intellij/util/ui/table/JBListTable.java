// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.table;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.EditorSettingsProvider;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TableUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.MouseEventHandler;
import com.intellij.util.ui.TimerUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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
import java.util.function.IntConsumer;

import static java.awt.event.KeyEvent.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBListTable {
  protected final JBTable myInternalTable;
  private final JBTable myMainTable;
  private final RowResizeAnimator myRowResizeAnimator;
  protected MouseEvent myMouseEvent;
  private MyCellEditor myCellEditor;
  private int myLastFocusedEditorComponentIdx = -1;

  public JBListTable(@NotNull JBTable t, @NotNull Disposable parent) {
    myInternalTable = t;
    myMainTable = new MyTable();
    myMainTable.setTableHeader(null);
    myMainTable.setShowGrid(false);
    myRowResizeAnimator = new RowResizeAnimator(myMainTable);
    Disposer.register(parent, myRowResizeAnimator);
  }

  public void setVisibleRowCount(int rowCount) {
    myInternalTable.setVisibleRowCount(rowCount);
  }

  public void stopEditing() {
    TableUtil.stopEditing(myMainTable);
  }

  private static void installPaddingAndBordersForEditors(JBTableRowEditor editor) {
    final List<EditorTextField> editors = UIUtil.findComponentsOfType(editor, EditorTextField.class);
    for (EditorTextField textField : editors) {
      textField.putClientProperty(ComboBox.IS_TABLE_CELL_EDITOR_PROPERTY, Boolean.FALSE);
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

    Font font = EditorFontType.getGlobalPlainFont();
    font = new Font(font.getFontName(), font.getStyle(), JBUIScale.scaleFontSize((float)12));
    field.setFont(font);
    field.addSettingsProvider(EditorSettingsProvider.NO_WHITESPACE);

    if (selected && focused) {
      panel.setBackground(UIUtil.getTableSelectionBackground(true));
      field.setAsRendererWithSelection(UIUtil.getTableSelectionBackground(true), UIUtil.getTableSelectionForeground(true));
    } else {
      panel.setBackground(UIUtil.getTableBackground());
      if (selected) {
        panel.setBorder(new DottedBorder(UIUtil.getTableForeground()));
      }
    }
    panel.add(field, BorderLayout.WEST);
    return panel;
  }

  private final class MyCellEditor extends AbstractTableCellEditor {
    private final JBTableRowEditor myEditor;

    MyCellEditor(JBTableRowEditor editor) {
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
          validate();
          int height = getPreferredSize().height;
          if (height > table.getRowHeight(row)) {
            myRowResizeAnimator.resize(row, height);
          }
        }

        @Override
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

  private static final class RowResizeAnimator implements ActionListener, Disposable {
    private static final int ANIMATION_STEP_MILLIS = 15;
    private static final int RESIZE_AMOUNT_PER_STEP = 5;

    private final Int2ObjectMap<RowAnimationState> myRowAnimationStates = new Int2ObjectOpenHashMap<>();
    private final Timer myAnimationTimer = TimerUtil.createNamedTimer("JBListTableTimer", ANIMATION_STEP_MILLIS, this);
    private final JTable myTable;
    private boolean myDisposed;

    RowResizeAnimator(JTable table) {
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

    void revive() {
      myDisposed = false;
    }

    @Override
    public void dispose() {
      myDisposed = true;
      stopAnimation();
      // enforce all animations are completed
      doAnimationStep(Long.MAX_VALUE);
    }

    private void startAnimation() {
      if (!myAnimationTimer.isRunning() && !myDisposed) {
        myAnimationTimer.start();
      }
    }

    private void stopAnimation() {
      myAnimationTimer.stop();
    }

    private void doAnimationStep(long updateTime) {
      IntList completeRows = new IntArrayList(myRowAnimationStates.size());
      for (Int2ObjectMap.Entry<RowAnimationState> entry : myRowAnimationStates.int2ObjectEntrySet()) {
        if (entry.getValue().doAnimationStep(updateTime)) {
          completeRows.add(entry.getIntKey());
        }
      }
      completeRows.forEach((IntConsumer)row -> {
        myRowAnimationStates.remove(row);
      });
      if (myRowAnimationStates.isEmpty()) {
        stopAnimation();
      }
    }

    private final class RowAnimationState {
      private final int myRow;
      private final int myTargetHeight;
      private long myLastUpdateTime;

      RowAnimationState(int row, int targetHeight) {
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

        TableUtil.scrollSelectionToVisible(myTable);

        return myTargetHeight == newHeight;
      }
    }
  }

  private final class MyTable extends JBTable {

    MyTable() {
      super(new MyTableModel(myInternalTable.getModel()));
    }

    @Override
    public MyTableModel getModel() {
      return (MyTableModel)super.getModel();
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
        editor.addMouseListener(MouseEventHandler.CONSUMER);

        myCellEditor = new MyCellEditor(editor);
        return myCellEditor;
      }
      myCellEditor = null;
      return null;
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
      myRowResizeAnimator.revive();
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      Disposer.dispose(myRowResizeAnimator);
    }
  }

  private final class MyTableModel extends JBListTableModel {

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
