package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxPropertyReference;

import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxPropertyRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final PsiReference reference = getReference(dataContext);
    return reference != null;
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    //if (editor == null) {
    //  editor = CommonDataKeys.EDITOR.getData(dataContext);
    //}
    //final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    final JavaFxPropertyReference reference = getReference(dataContext);
    if (reference == null) return;
    if (reference.isRenameable()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
        assert newName != null : "Rename property";
        doRename(reference, newName, false, false);
        return;
      }
      final Map<PsiElement, String> elementsToRename = reference.getElementsToRename("a");
      for (PsiElement element : elementsToRename.keySet()) {
        if (!PsiElementRenameHandler.canRename(project, editor, element)) return;
      }
      final PsiElement psiElement = JavaFxPropertyElement.fromReference(reference);
      if (psiElement != null) {
        new PropertyRenameDialog(reference, psiElement, project, editor).show();
      }
    }
    else {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot rename built-in property", null, null);
    }
  }

  private static void doRename(JavaFxPropertyReference reference, String newName, final boolean searchInComments, boolean isPreview) {
    final PsiElement psiElement = JavaFxPropertyElement.fromReference(reference);
    if (psiElement == null) return;
    final RenameRefactoring rename = new JavaRenameRefactoringImpl(psiElement.getProject(), psiElement, newName, searchInComments, false);
    rename.setPreviewUsages(isPreview);

    final Map<PsiElement, String> elementsToRename = reference.getElementsToRename(newName);
    for (Map.Entry<PsiElement, String> entry : elementsToRename.entrySet()) {
      rename.addElement(entry.getKey(), entry.getValue());
    }
    rename.run();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    final PsiReference reference = getReference(dataContext);
    //final PsiElement element = getElement(dataContext);

  }

  @Nullable
  private static JavaFxPropertyReference getReference(DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);

    //if (file == null && editor != null && ApplicationManager.getApplication().isUnitTestMode()) {
    //  final Project project = editor.getProject();
    //  if (project != null) {
    //    file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    //  }
    //}

    if (editor != null && file instanceof XmlFile && JavaFxFileTypeFactory.isFxml(file)) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiReference reference = file.findReferenceAt(offset);
      if (reference instanceof JavaFxPropertyReference) {
        return (JavaFxPropertyReference)reference;
      }
    }

    return null;
  }

  private static class PropertyRenameDialog extends RenameDialog {

    private final JavaFxPropertyReference myPropertyReference;

    protected PropertyRenameDialog(@NotNull JavaFxPropertyReference propertyReference,
                                   @NotNull PsiElement psiElement,
                                   @NotNull Project project,
                                   Editor editor) {
      super(project, psiElement, null, editor);
      myPropertyReference = propertyReference;
    }

    protected void doAction() {
      final String newName = getNewName();
      final boolean searchInComments = isSearchInComments();
      doRename(myPropertyReference, newName, searchInComments, isPreviewUsages());
      close(DialogWrapper.OK_EXIT_CODE);
    }
  }
}
