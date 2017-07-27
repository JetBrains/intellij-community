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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.NotNullFunction;
import org.jetbrains.idea.devkit.util.PointableCandidate;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class LineMarkerInfoHelper {
  private static final String EXTENSION_TOOLTIP_PATTERN =
    "<a href=\"#navigation/{0}:{1}\">{2}</a> extension declaration in <a href=\"#navigation/{3}:0\">{4}</a>";
  private static final String EXTENSION_POINT_TOOLTIP_PATTERN =
    "<a href=\"#navigation/{0}:{1}\">{2}</a> extension point declaration in <a href=\"#navigation/{3}:0\">{4}</a>";

  private static final NotNullFunction<PointableCandidate, Collection<? extends PsiElement>> CONVERTER =
    candidate -> Collections.singleton(candidate.pointer.getElement());

  private static final NotNullFunction<PointableCandidate, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    candidate -> GotoRelatedItem.createItems(Collections.singleton(candidate.pointer.getElement()), "DevKit");


  private LineMarkerInfoHelper() {
  }

  public static RelatedItemLineMarkerInfo<PsiElement> createExtensionLineMarkerInfo(List<? extends PointableCandidate> targets,
                                                                                 PsiElement element) {
    return createPluginLineMarkerInfo(targets, element, "Choose Extension", createExtensionTooltip(targets));
  }

  public static RelatedItemLineMarkerInfo<PsiElement> createExtensionPointLineMarkerInfo(List<? extends PointableCandidate> targets,
                                                                                    PsiElement element) {
    return createPluginLineMarkerInfo(targets, element, "Choose Extension Point", createExtensionPointTooltip(targets));
  }

  private static RelatedItemLineMarkerInfo<PsiElement> createPluginLineMarkerInfo(List<? extends PointableCandidate> targets,
                                                                                  PsiElement element, String popup, String tooltip) {
    return NavigationGutterIconBuilder
      .create(AllIcons.Nodes.Plugin, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(targets)
      .setPopupTitle(popup)
      .setTooltipText(tooltip)
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(element);
  }

  private static String createExtensionTooltip(List<? extends PointableCandidate> targets) {
    return createPluginTooltip(targets, EXTENSION_TOOLTIP_PATTERN, XmlTag::getName);
  }

  private static String createExtensionPointTooltip(List<? extends PointableCandidate> targets) {
    return createPluginTooltip(targets, EXTENSION_POINT_TOOLTIP_PATTERN, tag -> {
      String name = tag.getAttributeValue("name");
      if (StringUtil.isEmpty(name)) {
        // shouldn't happen, just for additional safety
        name = "Extension Point";
      }
      return name;
    });
  }

  private static String createPluginTooltip(List<? extends PointableCandidate> targets, String tooltipPattern,
                                            NotNullFunction<XmlTag, String> nameProvider) {
    StringBuilder result = new StringBuilder("<html><body>");

    for (int i = 0; i < targets.size(); i++) {
      PointableCandidate target = targets.get(i);
      PsiElement element = target.pointer.getElement();
      if (element == null) {
        // shouldn't happen
        throw new NullPointerException("Null element for pointable candidate: " + target);
      }

      XmlTag tag = (XmlTag)element;
      PsiFile file = tag.getContainingFile();
      String path = file.getVirtualFile().getPath();
      result.append(MessageFormat.format(tooltipPattern,
                                         path, String.valueOf(tag.getTextRange().getStartOffset()), nameProvider.fun(tag),
                                         path, file.getName()));

      if (i < targets.size() - 1) {
        result.append("<br/>");
      }
    }

    result.append("</body></html>");
    return result.toString();
  }
}
