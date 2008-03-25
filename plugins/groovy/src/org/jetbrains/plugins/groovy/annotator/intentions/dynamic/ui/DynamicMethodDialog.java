package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.util.List;
import java.util.EventObject;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicMethodDialog extends DynamicDialog {
  public DynamicMethodDialog(GrReferenceExpression referenceExpression) {
    super(referenceExpression);

    final DItemElement methodElement = getItemElement();

    assert methodElement instanceof DMethodElement;

    setupParameterTable(((DMethodElement) methodElement).getPairs());
    setupParameterList(((DMethodElement) methodElement).getPairs());
    setTitle(GroovyBundle.message("add.dynamic.method"));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.return.type"));
  }

  private void setupParameterTable(final List<MyPair> pairs) {
    final JTable table = getParametersTable();

    MySuggestedNameCellEditor suggestedNameCellEditor = new MySuggestedNameCellEditor(QuickfixUtil.getArgumentsNames(pairs));
    table.setDefaultEditor(String.class, suggestedNameCellEditor);

    suggestedNameCellEditor.addCellEditorListener(new CellEditorListener() {
      public void editingStopped(ChangeEvent e) {
        final int editingColumn = table.getSelectedColumn();
        if (editingColumn != 0) return;

        final int editingRow = table.getSelectedRow();
        if (editingRow < 0 || editingRow >= pairs.size()) return;

        String newNameValue = ((MySuggestedNameCellEditor) e.getSource()).getCellEditorValue();

        final MyPair editingPair = pairs.get(editingRow);
        editingPair.setFirst(newNameValue);
      }

      public void editingCanceled(ChangeEvent e) {
      }
    });
  }

  protected boolean isTableVisible() {
    return true;
  }

  private void setupParameterList(List<MyPair> arguments) {
    final JTable table = getParametersTable();

    //TODO: add header
    final ListTableModel<MyPair> dataModel = new ListTableModel<MyPair>(new NameColumnInfo(), new TypeColumnInfo());
    dataModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        fireDataChanged();
      }
    });
    dataModel.setItems(arguments);
    table.setModel(dataModel);
    if (!arguments.isEmpty()) {
      String max0 = arguments.get(0).first;
      String max1 = arguments.get(0).second;
      for (MyPair argument : arguments) {
        if (argument.first.length() > max0.length()) max0 = argument.first;
        if (argument.second.length() > max1.length()) max1 = argument.second;
      }

      final FontMetrics metrics = table.getFontMetrics(table.getFont());
      final TableColumn column0 = table.getColumnModel().getColumn(0);
      column0.setPreferredWidth(metrics.stringWidth(max0 + "  "));

      final TableColumn column1 = table.getColumnModel().getColumn(1);
      column1.setPreferredWidth(metrics.stringWidth(max1 + "  "));
    }
  }


  private class TypeColumnInfo extends ColumnInfo<MyPair, String> {
    public TypeColumnInfo() {
      super(GroovyBundle.message("dynamic.name"));
    }

    public String valueOf(MyPair pair) {
      return pair.second;
    }

    public boolean isCellEditable(MyPair stringPsiTypeMyPair) {
      return false;
    }

    public void setValue(MyPair pair, String value) {
      PsiType type = null;
      try {
        type = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(value).getType();
      } catch (IncorrectOperationException e) {
      }

      if (type == null) return;
      pair.setSecond(type.getCanonicalText());
    }
  }

  private static class NameColumnInfo extends ColumnInfo<MyPair, String> {
    public NameColumnInfo() {
      super(GroovyBundle.message("dynamic.type"));
    }

    public boolean isCellEditable(MyPair myPair) {
      return true;
    }

    public String valueOf(MyPair pair) {
      return pair.first;
    }
  }

  protected void updateOkStatus() {
    super.updateOkStatus();

    if (getParametersTable().isEditing()) setOKActionEnabled(false);
  }

  private static class MySuggestedNameCellEditor extends AbstractTableCellEditor {
    final JTextField myNameField;

    public MySuggestedNameCellEditor(String[] names) {
      myNameField = new JTextField(names[0]);
    }

    public String getCellEditorValue() {
      return myNameField.getText();
    }

    public boolean isCellEditable(EventObject e) {
      return true;
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      if (value instanceof String) {
        myNameField.setText((String) value);
      }
      return myNameField;
    }
  }
}