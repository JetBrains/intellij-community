// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

public final class GroovyRenameInputValidator implements RenameInputValidator {

  @Override
  public @NotNull ElementPattern<? extends PsiElement> getPattern() {
    return PlatformPatterns.psiElement(GrNamedElement.class);
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return GroovyRefactoringUtil.isCorrectReferenceName(newName, element.getProject());
  }
}
