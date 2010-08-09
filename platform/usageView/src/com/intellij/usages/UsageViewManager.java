/*
 * Copyright 2000-2009 JetBrains s.r.o.
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


import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiElement;
import com.intellij.usages.rules.PsiElementUsage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class UsageViewManager {
  public static UsageViewManager getInstance (Project project) {
    return ServiceManager.getService(project, UsageViewManager.class);
  }

  @NotNull
  public abstract UsageView createUsageView(@NotNull UsageTarget[] targets, @NotNull Usage[] usages, @NotNull UsageViewPresentation presentation, Factory<UsageSearcher> usageSearcherFactory);

  @NotNull
  public abstract UsageView showUsages(@NotNull UsageTarget[] searchedFor, @NotNull Usage[] foundUsages, @NotNull UsageViewPresentation presentation, Factory<UsageSearcher> factory);

  @NotNull
  public abstract UsageView showUsages(@NotNull UsageTarget[] searchedFor, @NotNull Usage[] foundUsages, @NotNull UsageViewPresentation presentation);

  @Nullable ("in case no usages found or usage view not shown for one usage")
  public abstract UsageView searchAndShowUsages(@NotNull UsageTarget[] searchFor,
                                @NotNull Factory<UsageSearcher> searcherFactory,
                                boolean showPanelIfOnlyOneUsage,
                                boolean showNotFoundMessage,
                                @NotNull UsageViewPresentation presentation,
                                UsageViewStateListener listener);

  public abstract void setCurrentSearchCancelled(boolean flag);

  public abstract boolean searchHasBeenCancelled();

  public abstract void checkSearchCanceled() throws ProcessCanceledException;

  public interface UsageViewStateListener {
    void usageViewCreated(UsageView usageView);
    void findingUsagesFinished(final UsageView usageView);
  }

  public abstract void searchAndShowUsages(@NotNull UsageTarget[] searchFor,
                           @NotNull Factory<UsageSearcher> searcherFactory,
                           @NotNull FindUsagesProcessPresentation processPresentation,
                           @NotNull UsageViewPresentation presentation,
                           UsageViewStateListener listener);

  @Nullable
  public abstract UsageView getSelectedUsageView();

  public static boolean isSelfUsage(Usage usage, UsageTarget[] searchForTarget) {
    boolean selfUsage = false;

    if (!(usage instanceof PsiElementUsage)) return false;
    final PsiElement element = ((PsiElementUsage)usage).getElement();
    if (element == null) return false;

    for(UsageTarget ut:searchForTarget) {
      if (ut instanceof PsiElementUsageTarget) {
        if (isSelfUsage(element, ((PsiElementUsageTarget)ut).getElement())) {
          selfUsage = true;
          break;
        }
      }
    }

    return selfUsage;
  }

  public static boolean isSelfUsage(PsiElement element, PsiElement psiElement) {
    return element.getParent() == psiElement; // self usage might be configurable
  }
}
