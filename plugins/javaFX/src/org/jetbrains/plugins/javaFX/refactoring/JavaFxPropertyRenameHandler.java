package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxIdAttributeReference;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxPropertyReference;

import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxPropertyRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final PsiReference reference = getReference(dataContext);
    if (reference instanceof JavaFxPropertyReference) return true;
    if (reference instanceof JavaFxIdAttributeReference) return ((JavaFxIdAttributeReference)reference).isBuiltIn();
    return false;
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performInvoke(project, editor, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    performInvoke(project, null, dataContext);
  }

  private static void performInvoke(@NotNull Project project, @Nullable Editor editor, DataContext dataContext) {
    final PsiReference reference = getReference(dataContext);
    if (reference == null) return;
    if (reference instanceof JavaFxIdAttributeReference && ((JavaFxIdAttributeReference)reference).isBuiltIn()) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot rename built-in property", null, null);
      return;
    }
    if ((reference instanceof JavaFxPropertyReference) && reference.resolve() != null) {
      final JavaFxPropertyReference propertyReference = (JavaFxPropertyReference)reference;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
        assert newName != null : "Rename property";
        doRename(propertyReference, newName, false, false);
        return;
      }
      final Map<String, PsiElement> elementsToRename = getElementsToRename(propertyReference, "a");
      for (PsiElement element : elementsToRename.values()) {
        if (!PsiElementRenameHandler.canRename(project, editor, element)) return;
      }
      final PsiElement psiElement = JavaFxPropertyElement.fromReference(propertyReference);
      if (psiElement != null) {
        new PropertyRenameDialog(propertyReference, psiElement, project, editor).show();
      }
    }
  }

  private static void doRename(JavaFxPropertyReference reference, String newName, final boolean searchInComments, boolean isPreview) {
    final PsiElement psiElement = JavaFxPropertyElement.fromReference(reference);
    if (psiElement == null) return;
    final RenameRefactoring rename = new JavaRenameRefactoringImpl(psiElement.getProject(), psiElement, newName, searchInComments, false);
    rename.setPreviewUsages(isPreview);

    final Map<String, PsiElement> elementsToRename = getElementsToRename(reference, newName);
    for (Map.Entry<String, PsiElement> entry : elementsToRename.entrySet()) {
      rename.addElement(entry.getValue(), entry.getKey());
    }
    rename.run();
  }

  @Nullable
  private static PsiReference getReference(DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);

    if (file == null && editor != null && ApplicationManager.getApplication().isUnitTestMode()) {
      final Project project = editor.getProject();
      if (project != null) {
        file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      }
    }

    if (editor != null && file instanceof XmlFile && JavaFxFileTypeFactory.isFxml(file)) {
      final int offset = editor.getCaretModel().getOffset();
      return file.findReferenceAt(offset);
    }

    return null;
  }

  @NotNull
  private static Map<String, PsiElement> getElementsToRename(@NotNull JavaFxPropertyReference reference, @NotNull String newPropertyName) {
    final Map<String, PsiElement> rename = new THashMap<>();
    ContainerUtil.putIfNotNull(newPropertyName, reference.getField(), rename);
    ContainerUtil.putIfNotNull(PropertyUtil.suggestGetterName(newPropertyName, reference.getType()), reference.getGetter(), rename);
    ContainerUtil.putIfNotNull(PropertyUtil.suggestSetterName(newPropertyName), reference.getSetter(), rename);
    ContainerUtil.putIfNotNull(newPropertyName + JavaFxCommonNames.PROPERTY_METHOD_SUFFIX, reference.getObservableGetter(), rename);
    //TODO add "name" parameter of the observable property constructor (like new SimpleObjectProperty(this, "name", null);
    return rename;
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
