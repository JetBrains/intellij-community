package org.jetbrains.android.refactoring;

import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
abstract class AndroidBaseXmlRefactoringAction extends BaseRefactoringAction {
  @Override
  protected boolean isAvailableOnElementInEditorAndFile(PsiElement element, Editor editor, PsiFile file, DataContext context) {
    if (element == null ||
        AndroidFacet.getInstance(element) == null ||
        PsiTreeUtil.getParentOfType(element, XmlText.class) != null) {
      return false;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    return tag != null && isEnabled(tag);
  }

  @Override
  protected boolean isEnabledOnElements(PsiElement[] elements) {
    if (elements.length != 1) {
      return false;
    }
    final PsiElement element = elements[0];
    return element instanceof XmlTag &&
           AndroidFacet.getInstance(element) != null &&
           isEnabled((XmlTag)element);
  }

  protected abstract boolean isEnabled(@NotNull XmlTag tag);

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return language == XMLLanguage.INSTANCE;
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return file instanceof XmlFile &&
           AndroidFacet.getInstance(file) != null &&
           isMyFile(file);
  }

  protected abstract boolean isMyFile(PsiFile file);

  @Override
  public void update(AnActionEvent e) {
    final DataContext context = e.getDataContext();

    final DataContext patchedContext = new DataContext() {
      @Override
      public Object getData(@NonNls String dataId) {
        final Object data = context.getData(dataId);
        if (data != null) {
          return data;
        }
        if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
          return getXmlTagFromExternalContext(context);
        }
        return null;
      }
    };
    super.update(new AnActionEvent(e.getInputEvent(), patchedContext, e.getPlace(), e.getPresentation(),
                                   e.getActionManager(), e.getModifiers()));
  }

  protected abstract void doRefactor(@NotNull Project project, @NotNull XmlTag tag);

  @Override
  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    final XmlTag componentTag = getXmlTagFromExternalContext(dataContext);
    return new MyHandler(componentTag);
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Nullable
  protected static XmlTag getXmlTagFromExternalContext(DataContext dataContext) {
    if (dataContext == null) {
      return null;
    }

    for (AndroidRefactoringContextProvider provider : AndroidRefactoringContextProvider.EP_NAME.getExtensions()) {
      final XmlTag componentTag = provider.getComponentTag(dataContext);

      if (componentTag != null) {
        return componentTag;
      }
    }
    return null;
  }

  private class MyHandler implements RefactoringActionHandler {
    private final XmlTag myTagFromExternalContext;

    private MyHandler(@Nullable XmlTag tagFromExternalContext) {
      myTagFromExternalContext = tagFromExternalContext;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
      if (myTagFromExternalContext != null) {
        doRefactor(project, myTagFromExternalContext);
        return;
      }
      final PsiElement element = getElementAtCaret(editor, file);
      if (element == null) {
        return;
      }
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag == null) {
        return;
      }
      doRefactor(project, tag);
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
      if (myTagFromExternalContext != null) {
        doRefactor(project, myTagFromExternalContext);
        return;
      }
      if (elements.length != 1) {
        return;
      }
      final PsiElement element = elements[0];
      if (!(element instanceof XmlTag)) {
        return;
      }
      doRefactor(project, (XmlTag)element);
    }
  }
}
