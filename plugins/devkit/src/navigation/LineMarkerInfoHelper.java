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
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.PointableCandidate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class LineMarkerInfoHelper {
  private static final NotNullFunction<PointableCandidate, Collection<? extends PsiElement>> CONVERTER =
    candidate -> Collections.singleton(candidate.pointer.getElement());
  private static final NotNullFunction<PointableCandidate, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    candidate -> GotoRelatedItem.createItems(Collections.singleton(candidate.pointer.getElement()), "DevKit");


  private static final NullableFunction<PointableCandidate, String> EXTENSION_NAMER =
    createNamer("line.marker.tooltip.extension.declaration", XmlTag::getName);

  private static final NullableFunction<PointableCandidate, String> EXTENSION_POINT_NAMER =
    createNamer("line.marker.tooltip.extension.point.declaration", tag -> {
      String name = tag.getAttributeValue("name");
      if (StringUtil.isEmpty(name)) {
        // shouldn't happen, just for additional safety
        name = "Extension Point";
      }
      return name;
    });


  private LineMarkerInfoHelper() {
  }


  @NotNull
  public static RelatedItemLineMarkerInfo<PsiElement> createExtensionLineMarkerInfo(List<? extends PointableCandidate> targets,
                                                                                 PsiElement element) {
    return createPluginLineMarkerInfo(targets, element, "Choose Extension", EXTENSION_NAMER);
  }

  @NotNull
  public static RelatedItemLineMarkerInfo<PsiElement> createExtensionPointLineMarkerInfo(List<? extends PointableCandidate> targets,
                                                                                    PsiElement element) {
    return createPluginLineMarkerInfo(targets, element, "Choose Extension Point", EXTENSION_POINT_NAMER);
  }

  @NotNull
  private static RelatedItemLineMarkerInfo<PsiElement> createPluginLineMarkerInfo(List<? extends PointableCandidate> targets,
                                                                                  PsiElement element, String popup,
                                                                                  NullableFunction<PointableCandidate, String> namer) {
    return NavigationGutterIconBuilder
      .create(AllIcons.Nodes.Plugin, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(targets)
      .setPopupTitle(popup)
      .setNamer(namer)
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(element);
  }

  @NotNull
  private static NullableFunction<PointableCandidate, String> createNamer(String tooltipPatternPropertyName,
                                                                          NotNullFunction<XmlTag, String> nameProvider) {
    return target -> {
      XmlTag tag = target.pointer.getElement();
      if (tag == null) {
        // shouldn't happen
        throw new NullPointerException("Null element for pointable candidate: " + target);
      }

      PsiFile file = tag.getContainingFile();
      String path = file.getVirtualFile().getPath();
      return DevKitBundle.message(tooltipPatternPropertyName,
                                         path, String.valueOf(tag.getTextRange().getStartOffset()), nameProvider.fun(tag),
                                         path, file.getName());
    };
  }
}
