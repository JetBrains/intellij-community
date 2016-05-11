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
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.NotNullProducer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxComponentIdReferenceProvider;
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
    performInvoke(project, editor, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    performInvoke(project, null, dataContext);
  }

  private static void performInvoke(@NotNull Project project, @Nullable Editor editor, DataContext dataContext) {
    PsiReference reference = getReference(dataContext);
    if (reference == null) return;
    if (reference instanceof JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase &&
        ((JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase)reference).isBuiltIn()) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot rename built-in property", null, null);
      return;
    }
    if (reference instanceof JavaFxPropertyReference && reference.resolve() != null) {
      final JavaFxPropertyReference propertyReference = (JavaFxPropertyReference)reference;
      final Map<PsiElement, String> elementsToRename = getElementsToRename(propertyReference, "a");
      final boolean cannotRename = !elementsToRename.isEmpty() &&
                                   elementsToRename.keySet().stream()
                                     .anyMatch(element -> !PsiElementRenameHandler.canRename(project, editor, element));
      if (cannotRename) {
        return;
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
        assert newName != null : "Rename property";
        doRename(propertyReference, newName, false, false);
        return;
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

    final Map<PsiElement, String> elementsToRename = getElementsToRename(reference, newName);
    elementsToRename.forEach(rename::addElement);
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
      final PsiReference reference = file.findReferenceAt(offset);

      if (reference instanceof PsiMultiReference) {
        final PsiReference[] references = ((PsiMultiReference)reference).getReferences();
        for (PsiReference ref : references) {
          if (isKnown(ref)) {
            return ref;
          }
        }
      }
      if (isKnown(reference)) return reference;
    }

    return null;
  }

  private static boolean isKnown(PsiReference reference) {
    if (reference instanceof JavaFxPropertyReference) return true;
    if (reference instanceof JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase) {
      return ((JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase)reference).isBuiltIn();
    }
    return false;
  }

  @NotNull
  private static Map<PsiElement, String> getElementsToRename(@NotNull JavaFxPropertyReference reference, @NotNull String newPropertyName) {
    final Map<PsiElement, String> rename = new THashMap<>();
    putIfKeyNotNull(rename, reference.getGetter(), PropertyUtil.suggestGetterName(newPropertyName, reference.getType()));
    putIfKeyNotNull(rename, reference.getField(), newPropertyName);
    putIfKeyNotNull(rename, reference.getSetter(), PropertyUtil.suggestSetterName(newPropertyName));
    putIfKeyNotNull(rename, reference.getObservableGetter(), newPropertyName + JavaFxCommonNames.PROPERTY_METHOD_SUFFIX);
    putIfKeyNotNull(rename, reference.getStaticSetter(), PropertyUtil.suggestSetterName(newPropertyName));
    //TODO add "name" parameter of the observable property constructor (like new SimpleObjectProperty(this, "name", null);
    return rename;
  }

  private static <K, V> void putIfKeyNotNull(@NotNull Map<K, V> map, @Nullable K key, @NotNull V value) {
    if (key != null) map.put(key, value);
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
