// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename.inplace;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.JavaIdentifier;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public class GrMethodInplaceRenamer extends MemberInplaceRenamer {
  public GrMethodInplaceRenamer(PsiNamedElement elementToRename, PsiElement substituted, Editor editor) {
    super(elementToRename, substituted, editor);
  }

  @Override
  protected boolean isIdentifier(String newName, Language language) {
    return true;
  }

  @Override
  protected @NotNull TextRange getRangeToRename(@NotNull PsiElement element) {
    TextRange range = getIdentifierNameRange(element);
    return range != null ? range : super.getRangeToRename(element);
  }

  @Override
  protected @NotNull TextRange getRangeToRename(@NotNull PsiReference reference) {
    TextRange range = getReferenceNameRange(reference.getElement());
    return range != null ? range : super.getRangeToRename(reference);
  }

  private static @Nullable TextRange getIdentifierNameRange(@NotNull PsiElement element) {
    if (element instanceof JavaIdentifier) {
      return GrStringUtil.getStringContentRange(element.getNavigationElement());
    }
    return null;
  }

  private static @Nullable TextRange getReferenceNameRange(PsiElement element) {
    if (element instanceof GrReferenceExpression referenceExpression) {
      PsiElement nameElement = referenceExpression.getReferenceNameElement();
      TextRange stringContentRange = GrStringUtil.getStringContentRange(nameElement);
      if (stringContentRange != null) return stringContentRange.shiftRight(nameElement.getStartOffsetInParent());
    }
    return null;
  }
}
