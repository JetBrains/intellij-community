/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public abstract class UsageInfoSearcherAdapter implements UsageSearcher {
  protected void processUsages(final @NotNull Processor<Usage> processor, @NotNull Project project) {
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
      DumbService.getInstance(project).showDumbModeNotification("Usage search is not available until indices are ready");
      return;
    }
    final Usage[] usages = ApplicationManager.getApplication().runReadAction(new Computable<Usage[]>() {
      @Override
      public Usage[] compute() {
        return UsageInfo2UsageAdapter.convert(refUsages.get());
      }
    });

    for (final Usage usage : usages) {
      ApplicationManager.getApplication().runReadAction(() -> {
        processor.process(usage);
      });
    }
  }

  protected abstract UsageInfo[] findUsages();
}
