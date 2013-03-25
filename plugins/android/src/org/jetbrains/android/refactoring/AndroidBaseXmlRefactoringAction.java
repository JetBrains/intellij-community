package org.jetbrains.android.refactoring;

import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
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
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    final XmlTag[] tags = getXmlTagsFromExternalContext(context);
    if (tags.length > 0) {
      return AndroidFacet.getInstance(tags[0]) != null && isEnabledForTags(tags);
    }

    final TextRange range = getNonEmptySelectionRange(editor);
    if (range != null) {
      final Pair<PsiElement, PsiElement> psiRange = getExtractableRange(
        file, range.getStartOffset(), range.getEndOffset());
      return psiRange != null && isEnabledForPsiRange(psiRange.getFirst(), psiRange.getSecond());
    }

    if (element == null ||
        AndroidFacet.getInstance(element) == null ||
        PsiTreeUtil.getParentOfType(element, XmlText.class) != null) {
      return false;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    return tag != null && isEnabledForTags(new XmlTag[]{tag});
  }

  @Nullable
  private static TextRange getNonEmptySelectionRange(Editor editor) {
    if (editor != null) {
      final SelectionModel model = editor.getSelectionModel();

      if (model.hasSelection()) {
        final int start = model.getSelectionStart();
        final int end = model.getSelectionEnd();

        if (start < end) {
          return TextRange.create(start, end);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Pair<PsiElement, PsiElement> getExtractableRange(PsiFile file, int start, int end) {
    PsiElement startElement = file.findElementAt(start);
    PsiElement parent = startElement != null ? startElement.getParent() : null;

    while (parent != null &&
           !(parent instanceof PsiFile) &&
           parent.getTextRange().getStartOffset() == startElement.getTextRange().getStartOffset()) {
      startElement = parent;
      parent = parent.getParent();
    }
    PsiElement endElement = file.findElementAt(end - 1);
    parent = endElement != null ? endElement.getParent() : null;

    while (parent != null &&
           !(parent instanceof PsiFile) &&
           parent.getTextRange().getEndOffset() == endElement.getTextRange().getEndOffset()) {
      endElement = parent;
      parent = parent.getParent();
    }

    if (startElement == null || endElement == null) {
      return null;
    }
    final PsiElement commonParent = startElement.getParent();

    if (commonParent == null ||
        !(commonParent instanceof XmlTag) ||
        commonParent != endElement.getParent()) {
      return null;
    }
    PsiElement e = startElement;
    boolean containTag = false;

    while (e != null) {
      if (!(e instanceof XmlText) &&
          !(e instanceof XmlTag) &&
          !(e instanceof PsiWhiteSpace) &&
          !(e instanceof PsiComment)) {
        return null;
      }
      if (e instanceof XmlTag) {
        containTag = true;
      }
      if (e == endElement) {
        break;
      }
      e = e.getNextSibling();
    }
    return e != null && containTag
           ? Pair.create(startElement, endElement)
           : null;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    if (elements.length == 0) {
      return false;
    }
    final PsiElement element = elements[0];

    if (AndroidFacet.getInstance(element) == null) {
      return false;
    }
    final XmlTag[] tags = new XmlTag[elements.length];

    for (int i = 0; i < tags.length; i++) {
      if (!(elements[i] instanceof XmlTag)) {
        return false;
      }
      tags[i] = (XmlTag)elements[i];
    }
    return isEnabledForTags(tags);
  }

  protected abstract boolean isEnabledForTags(@NotNull XmlTag[] tags);

  protected boolean isEnabledForPsiRange(@NotNull PsiElement from, @Nullable PsiElement to) {
    return false;
  }

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
          final XmlTag[] tags = getXmlTagsFromExternalContext(context);
          return tags.length == 1 ? tags[0] : null;
        }
        else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
          return getXmlTagsFromExternalContext(context);
        }
        return null;
      }
    };
    super.update(new AnActionEvent(e.getInputEvent(), patchedContext, e.getPlace(), e.getPresentation(),
                                   e.getActionManager(), e.getModifiers()));
  }

  protected abstract void doRefactorForTags(@NotNull Project project, @NotNull XmlTag[] tags);

  protected void doRefactorForPsiRange(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement from, @NotNull PsiElement to) {
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    final XmlTag[] componentTags = getXmlTagsFromExternalContext(dataContext);
    return new MyHandler(componentTags);
  }

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @NotNull
  protected static XmlTag[] getXmlTagsFromExternalContext(DataContext dataContext) {
    if (dataContext == null) {
      return XmlTag.EMPTY;
    }

    for (AndroidRefactoringContextProvider provider : AndroidRefactoringContextProvider.EP_NAME.getExtensions()) {
      final XmlTag[] componentTags = provider.getComponentTags(dataContext);

      if (componentTags.length > 0) {
        return componentTags;
      }
    }
    return XmlTag.EMPTY;
  }

  private class MyHandler implements RefactoringActionHandler {
    private final XmlTag[] myTagsFromExternalContext;

    private MyHandler(@NotNull XmlTag[] tagsFromExternalContext) {
      myTagsFromExternalContext = tagsFromExternalContext;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
      if (myTagsFromExternalContext.length > 0) {
        doRefactorForTags(project, myTagsFromExternalContext);
        return;
      }

      final TextRange range = getNonEmptySelectionRange(editor);
      if (range != null) {
        final Pair<PsiElement, PsiElement> psiRange = getExtractableRange(
          file, range.getStartOffset(), range.getEndOffset());
        if (psiRange != null) {
          doRefactorForPsiRange(project, file, psiRange.getFirst(), psiRange.getSecond());
        }
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
      doRefactorForTags(project, new XmlTag[]{tag});
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
      if (myTagsFromExternalContext.length > 0) {
        doRefactorForTags(project, myTagsFromExternalContext);
        return;
      }
      if (elements.length != 1) {
        return;
      }
      final PsiElement element = elements[0];
      if (!(element instanceof XmlTag)) {
        return;
      }
      doRefactorForTags(project, new XmlTag[]{(XmlTag)element});
    }
  }
}
