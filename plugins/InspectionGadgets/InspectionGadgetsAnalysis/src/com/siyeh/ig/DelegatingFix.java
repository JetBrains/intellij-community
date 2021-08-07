// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DelegatingFix extends InspectionGadgetsFix implements Iconable, PriorityAction {

  protected final LocalQuickFix delegate;

  public DelegatingFix(LocalQuickFix delegate) {
    this.delegate = delegate;
  }

  @Override
  public @NotNull String getName() {
    return delegate.getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return delegate.getFamilyName();
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    delegate.applyFix(project, descriptor);
  }

  @Override
  public boolean startInWriteAction() {
    return delegate.startInWriteAction();
  }

  @Override
  public boolean availableInBatchMode() {
    return delegate.availableInBatchMode();
  }

  @Override
  public Icon getIcon(int flags) {
    return delegate instanceof Iconable ? ((Iconable)delegate).getIcon(flags) : null;
  }

  @NotNull
  @Override
  public Priority getPriority() {
    return delegate instanceof PriorityAction ? ((PriorityAction)delegate).getPriority() : Priority.NORMAL;
  }

  @Override
  public @Nullable LocalQuickFix getFileModifierForPreview(@NotNull PsiFile target) {
    LocalQuickFix fix = ObjectUtils.tryCast(delegate.getFileModifierForPreview(target), LocalQuickFix.class);
    if (fix == null) return null;
    if (fix == delegate) return this;
    DelegatingFix newFix = new DelegatingFix(fix);
    newFix.setOnTheFly(isOnTheFly());
    return newFix;
  }
}