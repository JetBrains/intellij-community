// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import icons.DevkitIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.util.PointableCandidate;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;

final class LineMarkerInfoHelper {
  private static final NotNullFunction<PointableCandidate, Collection<? extends PsiElement>> CONVERTER =
    candidate -> ContainerUtil.createMaybeSingletonList(candidate.pointer.getElement());
  private static final NotNullFunction<PointableCandidate, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    candidate -> GotoRelatedItem.createItems(ContainerUtil.createMaybeSingletonList(candidate.pointer.getElement()), "DevKit");

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

  private static final String MODULE_SUFFIX_PATTERN = " <font color='" + ColorUtil.toHex(UIUtil.getInactiveTextColor()) + "'>[{0}]</font>";

  private LineMarkerInfoHelper() {
  }

  @NotNull
  static RelatedItemLineMarkerInfo<PsiElement> createExtensionLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                             @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element, "Choose Extension", EXTENSION_NAMER);
  }

  @NotNull
  static RelatedItemLineMarkerInfo<PsiElement> createExtensionPointLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                                  @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element, "Choose Extension Point", EXTENSION_POINT_NAMER);
  }

  @NotNull
  private static RelatedItemLineMarkerInfo<PsiElement> createPluginLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                                  @NotNull PsiElement element, String popup,
                                                                                  NullableFunction<PointableCandidate, String> namer) {
    return NavigationGutterIconBuilder
      .create(DevkitIcons.Gutter.Plugin, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(targets)
      .setPopupTitle(popup)
      .setNamer(namer)
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(element);
  }

  @NotNull
  private static NullableFunction<PointableCandidate, String> createNamer(@PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String tooltipPatternPropertyName,
                                                                          NotNullFunction<? super XmlTag, String> nameProvider) {
    return target -> {
      XmlTag tag = target.pointer.getElement();
      if (tag == null) {
        // shouldn't happen
        throw new NullPointerException("Null element for pointable candidate: " + target);
      }

      PsiFile file = tag.getContainingFile();
      String path = file.getVirtualFile().getPath();

      String fileDisplayName = file.getName();
      Module module = ModuleUtilCore.findModuleForPsiElement(file);
      if (module != null) {
        fileDisplayName += MessageFormat.format(MODULE_SUFFIX_PATTERN, module.getName());
      }

      return DevKitBundle.message(tooltipPatternPropertyName,
                                  path, String.valueOf(tag.getTextRange().getStartOffset()), nameProvider.fun(tag),
                                  fileDisplayName);
    };
  }
}
