package org.jetbrains.android;

import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRenameHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return false;
    }

    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return false;
    }

    return AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file) != null;
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    if (file == null || editor == null) {
      return;
    }

    final XmlTag tag = AndroidUsagesTargetProvider.findValueResourceTagInContext(editor, file);
    if (tag == null) {
      return;
    }

    final XmlAttribute nameAttribute = tag.getAttribute("name");
    if (nameAttribute == null) {
      return;
    }

    final XmlAttributeValue attributeValue = nameAttribute.getValueElement();
    if (attributeValue == null) {
      return;
    }
    final RenameDialog dialog = new RenameDialog(project, new ValueResourceElementWrapper(attributeValue), null, editor);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final String name = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
      //noinspection TestOnlyProblems
      dialog.performRename(name);
      dialog.close(DialogWrapper.OK_EXIT_CODE);
    }
    else {
      dialog.show();
    }
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }

    final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return;
    }

    invoke(project, editor, file, dataContext);
  }

  @Override
  public String getActionTitle() {
    return "Rename Android value resource";
  }
}
