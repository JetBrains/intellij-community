// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiElement;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class UsageViewManager {
  public static UsageViewManager getInstance (Project project) {
    return project.getService(UsageViewManager.class);
  }

  public abstract @NotNull UsageView createUsageView(UsageTarget @NotNull [] targets,
                                                     Usage @NotNull [] usages,
                                                     @NotNull UsageViewPresentation presentation,
                                                     @Nullable Factory<? extends UsageSearcher> usageSearcherFactory);

  public abstract @NotNull UsageView showUsages(UsageTarget @NotNull [] searchedFor,
                                                Usage @NotNull [] foundUsages,
                                                @NotNull UsageViewPresentation presentation,
                                                @Nullable Factory<? extends UsageSearcher> factory);

  public abstract @NotNull UsageView showUsages(UsageTarget @NotNull [] searchedFor,
                                                Usage @NotNull [] foundUsages,
                                                @NotNull UsageViewPresentation presentation);

  public abstract @Nullable("returns null in case of no usages found or usage view not shown for one usage")
  UsageView searchAndShowUsages(UsageTarget @NotNull [] searchFor,
                                @NotNull Supplier<? extends UsageSearcher> searcherFactory,
                                boolean showPanelIfOnlyOneUsage,
                                boolean showNotFoundMessage,
                                @NotNull UsageViewPresentation presentation,
                                @Nullable UsageViewStateListener listener);

  public interface UsageViewStateListener {
    void usageViewCreated(@NotNull UsageView usageView);
    void findingUsagesFinished(@Nullable UsageView usageView);
  }

  public abstract void searchAndShowUsages(UsageTarget @NotNull [] searchFor,
                                           @NotNull Factory<? extends UsageSearcher> searcherFactory,
                                           @NotNull FindUsagesProcessPresentation processPresentation,
                                           @NotNull UsageViewPresentation presentation,
                                           @Nullable UsageViewStateListener listener);

  public abstract @Nullable UsageView getSelectedUsageView();

  public static boolean isSelfUsage(@NotNull Usage usage, UsageTarget @NotNull [] searchForTargets) {
    if (!(usage instanceof PsiElementUsage elementUsage)) return false;
    return ReadAction.compute(() -> {
      final PsiElement element = elementUsage.getElement();
      if (element == null) return false;
      PsiElement parent = element.getParent();
      if (parent == null) return false;
      int offset = element.getTextOffset();

      return ContainerUtil.exists(searchForTargets, ut -> {
        if (!(ut instanceof PsiElementUsageTarget t)) return false;
        PsiElement targetElement = t.getElement();
        return parent == targetElement && offset == targetElement.getTextOffset();
      });
    });
  }
}
