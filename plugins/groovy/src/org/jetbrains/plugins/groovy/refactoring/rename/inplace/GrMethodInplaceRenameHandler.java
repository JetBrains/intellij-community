// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename.inplace;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public final class GrMethodInplaceRenameHandler extends MemberInplaceRenameHandler {
  @Override
  protected boolean isAvailable(@Nullable PsiElement element, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!editor.getSettings().isVariableInplaceRenameEnabled()) return false;
    return element instanceof GrMethod && file instanceof GroovyFile;
  }

  @Override
  protected @NotNull MemberInplaceRenamer createMemberRenamer(@NotNull PsiElement element,
                                                              @NotNull PsiNameIdentifierOwner elementToRename,
                                                              @NotNull Editor editor) {
    return new GrMethodInplaceRenamer(elementToRename, element, editor);
  }
}
