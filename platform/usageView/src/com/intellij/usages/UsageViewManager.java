// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;


import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiElement;
import com.intellij.usages.rules.PsiElementUsage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UsageViewManager {
  public static UsageViewManager getInstance (Project project) {
    return ServiceManager.getService(project, UsageViewManager.class);
  }

  @NotNull
  public abstract UsageView createUsageView(UsageTarget @NotNull [] targets, Usage @NotNull [] usages, @NotNull UsageViewPresentation presentation, Factory<? extends UsageSearcher> usageSearcherFactory);

  @NotNull
  public abstract UsageView showUsages(UsageTarget @NotNull [] searchedFor, Usage @NotNull [] foundUsages, @NotNull UsageViewPresentation presentation, Factory<? extends UsageSearcher> factory);

  @NotNull
  public abstract UsageView showUsages(UsageTarget @NotNull [] searchedFor, Usage @NotNull [] foundUsages, @NotNull UsageViewPresentation presentation);

  @Nullable ("returns null in case of no usages found or usage view not shown for one usage")
  public abstract UsageView searchAndShowUsages(UsageTarget @NotNull [] searchFor,
                                                @NotNull Factory<? extends UsageSearcher> searcherFactory,
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

  @Nullable
  public abstract UsageView getSelectedUsageView();

  public static boolean isSelfUsage(@NotNull final Usage usage, final UsageTarget @NotNull [] searchForTarget) {
    if (!(usage instanceof PsiElementUsage)) return false;
    return ReadAction.compute(() -> {
      final PsiElement element = ((PsiElementUsage)usage).getElement();
      if (element == null) return false;

      for (UsageTarget ut : searchForTarget) {
        if (ut instanceof PsiElementUsageTarget) {
          if (isSelfUsage(element, ((PsiElementUsageTarget)ut).getElement())) {
            return true;
          }
        }
      }
      return false;
    });
  }

  private static boolean isSelfUsage(@NotNull PsiElement element, PsiElement psiElement) {
    return element.getParent() == psiElement; // self usage might be configurable
  }
}
