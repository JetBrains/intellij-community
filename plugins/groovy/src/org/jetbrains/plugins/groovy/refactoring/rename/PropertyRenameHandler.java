package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.RenameRefactoringImpl;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.AccessorMethod;

/**
 * @author ven
 */
public class PropertyRenameHandler implements RenameHandler {
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  private GrField getProperty(DataContext dataContext) {
    final PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
    if (element instanceof GrField && ((GrField) element).isProperty()) return (GrField) element;
    if (element instanceof AccessorMethod) return ((AccessorMethod) element).getProperty();
    return null;
  }

  public boolean isRenaming(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
    final GrField property = getProperty(dataContext);
    assert property != null;
    new PropertyRenameDialog(property, editor).show();
  }

  private static class PropertyRenameDialog extends RenameDialog {

    private final GrField myProperty;

    protected PropertyRenameDialog(GrField property, final Editor editor) {
      super(property.getProject(), property, null, editor);
      myProperty = property;
    }

    protected void doAction() {
      final String newName = getNewName();
      final boolean searchInComments = isSearchInComments();
      doRename(newName, searchInComments);
      close(DialogWrapper.OK_EXIT_CODE);
    }

    private void doRename(String newName, boolean searchInComments) {
      final RenameRefactoring rename = new RenameRefactoringImpl(myProperty.getProject(), myProperty, newName, searchInComments, false);

      final PsiMethod setter = myProperty.getSetter();
      if (setter != null) {
        final String setterName = PropertyUtil.suggestSetterName(newName);
        rename.addElement(setter, setterName);
      }

      final PsiMethod getter = myProperty.getGetter();
      if (getter != null) {
        final String getterName = PropertyUtil.suggestGetterName(newName, getter.getReturnType());
        rename.addElement(getter, getterName);
      }

      rename.run();
    }

  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
    throw new RuntimeException("Should not call");
  }
}
