package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicMethodDialog extends DynamicDialog {
  public DynamicMethodDialog(Module project, GrReferenceExpression referenceExpression) {
    super(project, referenceExpression);

//    setupParameterList(QuickfixUtil.getMethodArgumentsNames(referenceExpression.getParent()), QuickfixUtil.getMethodArgumentsTypes(((GrCall) referenceExpression.getParent())));
    setTitle(GroovyBundle.message("add.dynamic.method"));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.return.type"));
  }

  protected boolean isTableVisible() {
    return true;
  }

  private void setupParameterList(List<MyPair> arguments) {
    final JTable table = getTable();

    //TODO: add header
    final ListTableModel<MyPair> dataModel = new ListTableModel<MyPair>(new NameColumnInfo(), new TypeColumnInfo());
    dataModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        fireDataChanged();
      }
    });
    dataModel.setItems(arguments);
    table.setModel(dataModel);

  }


  private class TypeColumnInfo extends ColumnInfo<MyPair, String> {
    public TypeColumnInfo() {
      super(GroovyBundle.message("dynamic.name"));
    }

    //TODO: return PsiType
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

    public String valueOf(MyPair pair) {
      return pair.first;
    }
  }

  protected void updateOkStatus() {
    super.updateOkStatus();

    if (getTable().isEditing()) setOKActionEnabled(false);
  }
}
