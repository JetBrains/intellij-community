package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
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
  public DynamicMethodDialog(Project project, DynamicVirtualMethod virtualMethod, GrReferenceExpression referenceExpression) {
    super(project, virtualMethod, referenceExpression);

    setupParameterList(virtualMethod.getArguments());
    setTitle(GroovyBundle.message("add.dynamic.method"));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.return.type"));
  }

  protected boolean isTableVisible() {
    return true;
  }

  private void setupParameterList(List<MyPair<String, PsiType>> arguments) {
    final JTable table = getTable();

    final ListTableModel<MyPair<String, PsiType>> dataModel = new ListTableModel<MyPair<String, PsiType>>(new NameColumnInfo(), new TypeColumnInfo());
    dataModel.addTableModelListener(new TableModelListener(){
      public void tableChanged(TableModelEvent e) {
        fireDataChanged();
      }
    });
    dataModel.setItems(arguments);
    table.setModel(dataModel);
  }

  private class TypeColumnInfo extends ColumnInfo<MyPair<String, PsiType>, String> {
    public TypeColumnInfo() {
      super(GroovyBundle.message("dynamic.name"));
    }

    //TODO: return PsiType
    public String valueOf(MyPair<String, PsiType> MyPair) {
      return MyPair.getSecond().getCanonicalText();
    }

    public boolean isCellEditable(MyPair<String, PsiType> stringPsiTypeMyPair) {
      return false;
    }

    public void setValue(MyPair<String, PsiType> pair, String value) {
      PsiType type = null;
      try {
        type = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(value).getType();
      } catch (IncorrectOperationException e) {
      }

      if (type == null) return;
      pair.setSecond(type);
    }
  }

  private static class NameColumnInfo extends ColumnInfo<MyPair<String, PsiType>, String> {
    public NameColumnInfo() {
      super(GroovyBundle.message("dynamic.type"));
    }

    public String valueOf(MyPair<String, PsiType> pair) {
      return pair.getFirst();
    }
  }

  protected void updateOkStatus() {
    super.updateOkStatus();

    if (getTable().isEditing()) setOKActionEnabled(false);
  }


}
