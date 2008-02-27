package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.swing.*;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicMethodDialog extends DynamicDialog {
  public DynamicMethodDialog(Project project, DynamicVirtualMethod virtualMethod, GrReferenceExpression referenceExpression) {
    super(project, virtualMethod, referenceExpression);

    setupParameterList(virtualMethod.getArguments());
  }

  protected boolean isTableVisible() {
    return true;
  }

  private void setupParameterList(List<Pair<String,PsiType>> arguments) {
    final JTable table = getTable();

    final ListTableModel<Pair<String, PsiType>> dataModel = new ListTableModel<Pair<String, PsiType>>(new NameColumnInfo(), new TypeColumnInfo());
    dataModel.setItems(arguments);
    table.setModel(dataModel);
  }

  private static class TypeColumnInfo extends ColumnInfo<Pair<String, PsiType>, String> {
    public TypeColumnInfo() {
      super(GroovyBundle.message("dynamic.name"));
    }

    //TODO: return PsiType
    public String valueOf(Pair<String, PsiType> pair) {
      return pair.getSecond().getCanonicalText();
    }
  }

  private static class NameColumnInfo extends ColumnInfo<Pair<String, PsiType>, String> {
    public NameColumnInfo() {
      super(GroovyBundle.message("dynamic.type"));
    }

    public String valueOf(Pair<String, PsiType> pair) {
      return pair.getFirst();
    }
  }
}
