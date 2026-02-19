// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public abstract class UsageInfoSearcherAdapter implements UsageSearcher {
  protected void processUsages(final @NotNull Processor<? super Usage> processor, @NotNull Project project) {
    final Ref<UsageInfo[]> refUsages = new Ref<>();
    final Ref<Boolean> dumbModeOccurred = new Ref<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      try {
        refUsages.set(findUsages());
      }
      catch (IndexNotReadyException e) {
        dumbModeOccurred.set(true);
      }
    });
    if (!dumbModeOccurred.isNull()) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        UsageViewBundle.message("notification.usage.search.is.not.available.until.indices.are.ready"), DumbModeBlockedFunctionality.UsageInfoSearcherAdapter);
      return;
    }
    final Usage[] usages = ReadAction.compute(() -> UsageInfo2UsageAdapter.convert(refUsages.get()));

    for (final Usage usage : usages) {
      ApplicationManager.getApplication().runReadAction(() -> {
        processor.process(usage);
      });
    }
  }

  protected abstract UsageInfo @NotNull [] findUsages();
}
