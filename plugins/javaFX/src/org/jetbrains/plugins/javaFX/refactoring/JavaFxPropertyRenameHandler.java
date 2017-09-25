package org.jetbrains.plugins.javaFX.refactoring;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxComponentIdReferenceProvider;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxFieldIdReferenceProvider;
import org.jetbrains.plugins.javaFX.fxml.refs.JavaFxPropertyReference;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxPropertyRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final PsiReference reference = getKnownReference(getReferences(dataContext));
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
    final PsiReference[] references = getReferences(dataContext);
    final PsiReference reference = getKnownReference(references);
    if (reference == null) return;
    if (reference instanceof JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase &&
        ((JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase)reference).isBuiltIn()) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot rename built-in property", "Cannot rename", null);
      return;
    }
    if (reference instanceof JavaFxPropertyReference && reference.resolve() != null) {
      final JavaFxPropertyReference propertyReference = (JavaFxPropertyReference)reference;
      final Map<PsiElement, String> elementsToRename = getElementsToRename(propertyReference, "a");
      if (!canRename(project, editor, elementsToRename.keySet())) {
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
    if (reference instanceof JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef) {
      final XmlAttributeValue fxIdValueElement =
        ((JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef)reference).getXmlAttributeValue();
      final Set<PsiElement> elementsToRename = new THashSet<>();
      JavaFxRenameAttributeProcessor.visitReferencedElements(references, psiElement -> {
        if (psiElement != null) {
          elementsToRename.add(psiElement);
        }
      });
      ContainerUtil.addIfNotNull(elementsToRename, getNestedControllerField(fxIdValueElement));

      if (!canRename(project, editor, elementsToRename)) {
        return;
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
        assert newName != null : "Rename property";
        doRenameFxId(fxIdValueElement, newName, false, false);
      }
      else {
        new RenameFxIdDialog(fxIdValueElement, editor).show();
      }
    }
  }

  private static boolean canRename(@NotNull Project project, @Nullable Editor editor, @NotNull Collection<PsiElement> elements) {
    return elements.stream().allMatch(element -> PsiElementRenameHandler.canRename(project, editor, element));
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
  private static PsiReference getKnownReference(PsiReference[] references) {
    return ContainerUtil.find(references, JavaFxPropertyRenameHandler::isKnown);
  }

  @NotNull
  private static PsiReference[] getReferences(DataContext dataContext) {
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
        return ((PsiMultiReference)reference).getReferences();
      }
      if (isKnown(reference)) return new PsiReference[]{reference};
    }

    return PsiReference.EMPTY_ARRAY;
  }

  private static boolean isKnown(PsiReference reference) {
    if (reference instanceof JavaFxPropertyReference || reference instanceof JavaFxFieldIdReferenceProvider.JavaFxControllerFieldRef) {
      return true;
    }
    if (reference instanceof JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase) {
      return ((JavaFxComponentIdReferenceProvider.JavaFxIdReferenceBase)reference).isBuiltIn();
    }
    return false;
  }

  @NotNull
  private static Map<PsiElement, String> getElementsToRename(@NotNull JavaFxPropertyReference reference, @NotNull String newPropertyName) {
    final Map<PsiElement, String> rename = new THashMap<>();
    putIfKeyNotNull(rename, reference.getGetter(), PropertyUtilBase.suggestGetterName(newPropertyName, reference.getType()));
    putIfKeyNotNull(rename, reference.getField(), newPropertyName);
    putIfKeyNotNull(rename, reference.getSetter(), PropertyUtilBase.suggestSetterName(newPropertyName));
    putIfKeyNotNull(rename, reference.getObservableGetter(), newPropertyName + JavaFxCommonNames.PROPERTY_METHOD_SUFFIX);
    putIfKeyNotNull(rename, reference.getStaticSetter(), PropertyUtilBase.suggestSetterName(newPropertyName));
    //TODO add "name" parameter of the observable property constructor (like new SimpleObjectProperty(this, "name", null);
    return rename;
  }

  private static <K, V> void putIfKeyNotNull(@NotNull Map<K, V> map, @Nullable K key, @NotNull V value) {
    if (key != null) map.put(key, value);
  }

  public static void doRenameFxId(XmlAttributeValue fxIdValueElement, String newName, boolean searchInComments, boolean previewUsages) {
    final RenameRefactoring rename =
      new JavaRenameRefactoringImpl(fxIdValueElement.getProject(), fxIdValueElement, newName, searchInComments, false);
    rename.setPreviewUsages(previewUsages);

    final PsiField nestedControllerField = getNestedControllerField(fxIdValueElement);
    if (nestedControllerField != null) {
      rename.addElement(nestedControllerField, newName + FxmlConstants.CONTROLLER_SUFFIX);
    }
    rename.run();
  }

  @Nullable
  private static PsiField getNestedControllerField(@NotNull XmlAttributeValue fxIdValueElement) {
    final String fxId = fxIdValueElement.getValue();
    if (!StringUtil.isEmpty(fxId)) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(fxIdValueElement, XmlTag.class);
      if (tag != null && FxmlConstants.FX_INCLUDE.equals(tag.getName())) {
        final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(tag.getContainingFile());
        if (controllerClass != null) {
          return controllerClass.findFieldByName(fxId + FxmlConstants.CONTROLLER_SUFFIX, true);
        }
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

  private static class RenameFxIdDialog extends RenameDialog {
    public RenameFxIdDialog(@NotNull XmlAttributeValue fxIdValueElement, Editor editor) {
      super(fxIdValueElement.getProject(), fxIdValueElement, null, editor);
    }

    @Override
    protected void doAction() {
      final String newName = getNewName();
      final PsiElement element = getPsiElement();
      assert element instanceof XmlAttributeValue;
      doRenameFxId(((XmlAttributeValue)element), newName, isSearchInComments(), isPreviewUsages());
      close(DialogWrapper.OK_EXIT_CODE);
    }
  }
}
