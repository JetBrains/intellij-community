// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.codeInsight.internal;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.dev.codeInsight.internal.GoodCodeRedVisitor;
import com.intellij.dev.codeInsight.internal.LanguageGoodCodeRedVisitors;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class HighlightVisitorInternalInspection extends GoodCodeRedInspectionTool {

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  public @Nullable GoodCodeRedVisitor getGoodCodeRedVisitor(@NotNull PsiFile file) {
    return LanguageGoodCodeRedVisitors.INSTANCE.forLanguage(file.getLanguage());
  }
}
