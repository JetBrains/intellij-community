// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeInfoImpl;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureProcessor;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrParameterInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class RemoveUnusedGrParameterFix implements IntentionAction {
  private final String myName;

  public RemoveUnusedGrParameterFix(GrParameter parameter) {
    myName = parameter.getName();
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("remove.parameter.0", myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("remove.unused.parameter");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiElement at = psiFile.findElementAt(editor.getCaretModel().getOffset());
    GrParameter parameter = PsiTreeUtil.getParentOfType(at, GrParameter.class);

    return parameter != null && myName.equals(parameter.getName());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    GrParameter parameter = getParameter(editor, psiFile);
    if (parameter == null) return;

    if (!FileModificationService.getInstance().prepareFileForWrite(parameter.getContainingFile())) return;

    GrMethod method = (GrMethod)parameter.getDeclarationScope();
    GrChangeSignatureProcessor processor = new GrChangeSignatureProcessor(parameter.getProject(), createChangeInfo(method, parameter));
    processor.run();
  }

  private static @Nullable GrParameter getParameter(Editor editor, PsiFile file) {
    PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
    GrParameter parameter = PsiTreeUtil.getParentOfType(at, GrParameter.class);
    if (parameter == null) return null;
    return parameter;
  }

  private static GrChangeInfoImpl createChangeInfo(GrMethod method, GrParameter parameter) {
    List<GrParameterInfo> params = new ArrayList<>();
    int i = 0;
    for (GrParameter p : method.getParameterList().getParameters()) {
      if (p != parameter) {
        params.add(new GrParameterInfo(p, i));
      }
      i++;
    }

    GrTypeElement typeElement = method.getReturnTypeElementGroovy();
    CanonicalTypes.Type wrapper = typeElement != null ? CanonicalTypes.createTypeWrapper(method.getReturnType()) : null;
    return new GrChangeInfoImpl(method, null, wrapper, method.getName(), params, null, false);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    GrParameter parameter = getParameter(editor, psiFile);
    if (parameter == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    parameter.delete();
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
