// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.util.Ref;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class BaseRefactoringProcessorAccessor {
  private BaseRefactoringProcessorAccessor() {
  }
  
  public static boolean isPreviewUsages(@NotNull BaseRefactoringProcessor processor) {
    return processor.isPreviewUsages();
  }
  
  public static boolean isPreviewUsages(@NotNull BaseRefactoringProcessor processor, UsageInfo @NotNull [] usages) {
    return processor.isPreviewUsages(usages);
  }
  
  public static void execute(@NotNull BaseRefactoringProcessor processor, UsageInfo @NotNull [] usages) {
    processor.execute(usages);
  }

  public static UsageInfo @NotNull [] findUsages(@NotNull BaseRefactoringProcessor processor) {
    return processor.findUsages();
  }

  public static boolean preprocessUsages(BaseRefactoringProcessor processor, Ref<UsageInfo[]> usages) {
    return processor.preprocessUsages(usages);
  }
}
