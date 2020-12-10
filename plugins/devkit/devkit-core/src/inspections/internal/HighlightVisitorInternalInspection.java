// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HighlightVisitorInternalInspection extends GoodCodeRedInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  @Nullable
  public GoodCodeRedVisitor getGoodCodeRedVisitor(@NotNull PsiFile file) {
    return LanguageGoodCodeRedVisitors.INSTANCE.forLanguage(file.getLanguage());
  }
}
