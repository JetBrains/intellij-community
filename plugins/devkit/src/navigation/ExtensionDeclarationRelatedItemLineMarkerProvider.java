/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocator;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ExtensionDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedLineMarkerProviderBase {

  private static final NotNullFunction<ExtensionCandidate, Collection<? extends PsiElement>> EXTENSION_CONVERTER =
    candidate -> Collections.singleton(candidate.pointer.getElement());

  private static final NotNullFunction<ExtensionCandidate, Collection<? extends GotoRelatedItem>> EXTENSION_RELATED_ITEM_PROVIDER =
    candidate -> GotoRelatedItem.createItems(Collections.singleton(candidate.pointer.getElement()), "DevKit");


  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
    if (element instanceof PsiClass) {
      process((PsiClass)element, result);
    }
  }

  private static void process(PsiClass psiClass, Collection<? super RelatedItemLineMarkerInfo> result) {
    if (!ExtensionPointLocator.isRegisteredExtension(psiClass)) return;

    PsiIdentifier identifier = psiClass.getNameIdentifier();
    if (identifier == null) {
      return; //TODO review
    }

    ExtensionLocator locator = new ExtensionLocator(psiClass);
    //TODO ContainerUtil.filter() by extension impl class?
    List<ExtensionCandidate> targets = locator.findDirectCandidates();

    RelatedItemLineMarkerInfo<PsiElement> info = NavigationGutterIconBuilder
      .create(AllIcons.Nodes.Plugin, EXTENSION_CONVERTER, EXTENSION_RELATED_ITEM_PROVIDER)
      .setTargets(targets)
      .setPopupTitle("Choose Extension")
      .setTooltipText("Extension Declaration")
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(identifier);
    result.add(info);
  }
}
