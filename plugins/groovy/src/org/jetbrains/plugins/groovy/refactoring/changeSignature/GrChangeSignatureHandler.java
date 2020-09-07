// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureHandler implements ChangeSignatureHandler {

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = findTargetMember(file, editor);
    if (element == null) {
      element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    invokeOnElement(project, editor, element);
  }

  private static void invokeOnElement(Project project, Editor editor, PsiElement element) {
    if (element instanceof PsiMethod) {
      invoke((PsiMethod)element, project);
    }
    else {
      String message =
        RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("error.wrong.caret.position.method.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.message("changeSignature.refactoring.name"), HelpID.CHANGE_SIGNATURE);
    }
  }

  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, final DataContext dataContext) {
    if (elements.length != 1) return;
    Editor editor = dataContext == null ? null : CommonDataKeys.EDITOR.getData(dataContext);
    invokeOnElement(project, editor, elements[0]);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return null;
  }

  private static void invoke(PsiMethod method, final Project project) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;
    if (method instanceof GrReflectedMethod) method = ((GrReflectedMethod)method).getBaseMethod();

    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method);
    if (newMethod == null) return;

    if (!newMethod.equals(method)) {
      ChangeSignatureUtil.invokeChangeSignatureOn(newMethod, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return;

    if (!(method instanceof GrMethod)) return; //todo
    final GrChangeSignatureDialog dialog = new GrChangeSignatureDialog(project, new GrMethodDescriptor((GrMethod)method), true, null);
    dialog.show();
  }

  @Override
  @Nullable
  public PsiElement findTargetMember(@NotNull PsiFile file, @NotNull Editor editor) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiElement targetMember = findTargetMember(element);
    if (targetMember != null) return targetMember;

    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (reference != null) {
      return reference.resolve();
    }
    return null;
  }

  @Override
  @Nullable
  public PsiElement findTargetMember(@Nullable PsiElement element) {
    if (element == null) return null;

    final GrParameterList parameterList = PsiTreeUtil.getParentOfType(element, GrParameterList.class);
    if (parameterList != null) {
      final PsiElement parent = parameterList.getParent();
      if (parent instanceof PsiMethod) return parent;
    }

    if (element.getParent() instanceof GrMethod && ((GrMethod)element.getParent()).getNameIdentifierGroovy() == element) {
      return element.getParent();
    }
    final GrCall expression = PsiTreeUtil.getParentOfType(element, GrCall.class);
    if (expression != null) {
      return expression.resolveMethod();
    }

    final PsiReference ref = element.getReference();
    if (ref == null) return null;
    return ref.resolve();
  }
}
