// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtilBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroUtilKt;

/**
 * @author Maxim.Medvedev
 */
public class GrMacroElementRenamer extends RenamePsiElementProcessor {

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    final Language language = element.getLanguage();
    if (!GroovyLanguage.INSTANCE.equals(language)) {
      return false;
    }
    Pair<GrMethodCall, GroovyMacroTransformationSupport> macroInfo = GroovyMacroUtilKt.getMacroHandler(element);
    return macroInfo != null && macroInfo.getSecond().computeStaticReference(macroInfo.getFirst(), element) != null;
  }

  @Override
  public void renameElement(@NotNull PsiElement element,
                            @NotNull String newName,
                            UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    for (UsageInfo usage : usages) {
      RenameUtilBase.rename(usage, newName);
    }
    if (listener != null) {
      listener.elementRenamed(element);
    }
  }
}
