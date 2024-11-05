package org.jetbrains.intellij.plugins.journey.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import static com.intellij.openapi.project.Project.JOURNEY_CURRENT_NODE;

public final class JourneyNavigationUtils {


  public static PsiElement getPsiElement(SourceNavigationRequest navigationRequest, Project project) {
    PsiFile psiFile = VirtualFileUtil.findPsiFile(navigationRequest.getFile(), project);
    if (psiFile == null) return null;
    Integer offset = navigationRequest.getOffsetMarker() != null ? navigationRequest.getOffsetMarker().getStartOffset() : null;
    if (offset == null) return psiFile;
    PsiElement result = psiFile.findElementAt(offset);
    return result != null ? result : psiFile;
  }

  public static PsiElement getPsiElement(OpenFileDescriptor openFileDescriptor, Project project) {
    PsiFile psiFile = VirtualFileUtil.findPsiFile(openFileDescriptor.getFile(), project);
    if (psiFile == null) return null;
    int offset = openFileDescriptor.getOffset();
    PsiElement result = psiFile.findElementAt(offset);
    return result != null ? result : psiFile;
  }

  public static PsiMethod editorToPsiMethod(Project project, Editor editor) {
    // Get current caret offset
    CaretModel caretModel = editor.getCaretModel();
    int caretOffset = caretModel.getOffset();

    // Get the document corresponding to the editor
    Document document = editor.getDocument();

    // Get the PsiFile corresponding to the document
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

    // Get the PSI element at the caret offset
    PsiElement elementAtCaret = psiFile != null ? psiFile.findElementAt(caretOffset) : null;

    // Ascend the tree to find the enclosing PsiMethod
    return PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class);
  }

  public static PsiElement findPsiElement(Project project, Object element) {
    return ReadAction.nonBlocking(() -> {
      Object result = element;
      if (result instanceof UsageInfo usageInfo) result = usageInfo.getElement();
      if (result instanceof Editor editor) result = editorToPsiMethod(project, editor);
      if (result instanceof SourceNavigationRequest navigationRequest) result = getPsiElement(navigationRequest, project);
      if (result instanceof OpenFileDescriptor ofd) result = getPsiElement(ofd, project);
      if (result == null) result = project.getUserData(JOURNEY_CURRENT_NODE);
      if (result instanceof PsiElement psiElementFrom) {
        PsiElement parent = PsiUtil.tryFindParentOrNull(psiElementFrom, it -> it instanceof PsiMember);
        if (parent != null) {
          result = parent;
        }
      }
      if (result instanceof PsiElement psiElement) {
        return psiElement;
      }
      return null;
    }).executeSynchronously();
  }
}
