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
import com.intellij.psi.PsiElement;
import com.intellij.util.NotNullFunction;
import org.jetbrains.idea.devkit.util.PointableCandidate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class LineMarkerInfoHelper {

  private static final NotNullFunction<PointableCandidate, Collection<? extends PsiElement>> CONVERTER =
    candidate -> Collections.singleton(candidate.pointer.getElement());

  private static final NotNullFunction<PointableCandidate, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    candidate -> GotoRelatedItem.createItems(Collections.singleton(candidate.pointer.getElement()), "DevKit");


  private LineMarkerInfoHelper() {
  }

  public static RelatedItemLineMarkerInfo<PsiElement> createPluginLineMarkerInfo(List<? extends PointableCandidate> targets,
                                                                                 PsiElement element, String popup, String tooltip) {
    return NavigationGutterIconBuilder
      .create(AllIcons.Nodes.Plugin, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(targets)
      .setPopupTitle(popup)
      .setTooltipText(tooltip)
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(element);
  }
}
