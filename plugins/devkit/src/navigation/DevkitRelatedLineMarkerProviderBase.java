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
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collection;
import java.util.List;

/**
 * Do not process when current project is not a Plugin project.
 */
public abstract class DevkitRelatedLineMarkerProviderBase extends RelatedItemLineMarkerProvider {

  @Override
  public void collectNavigationMarkers(@NotNull List<PsiElement> elements,
                                       @NotNull Collection<? super RelatedItemLineMarkerInfo> result,
                                       boolean forNavigation) {
    final PsiElement psiElement = ContainerUtil.getFirstItem(elements);
    if (psiElement == null ||
        !PsiUtil.isPluginProject(psiElement.getProject())) {
      return;
    }

    super.collectNavigationMarkers(elements, result, forNavigation);
  }
}
