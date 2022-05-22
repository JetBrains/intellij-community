// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.DomGotoRelatedItem;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.util.ActionCandidate;
import org.jetbrains.idea.devkit.util.ComponentCandidate;
import org.jetbrains.idea.devkit.util.ListenerCandidate;
import org.jetbrains.idea.devkit.util.PointableCandidate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class LineMarkerInfoHelper {

  private static final NotNullFunction<PointableCandidate, Collection<? extends PsiElement>> CONVERTER =
    candidate -> ContainerUtil.createMaybeSingletonList(candidate.pointer.getElement());
  private static final NotNullFunction<PointableCandidate, Collection<? extends GotoRelatedItem>> RELATED_ITEM_PROVIDER =
    candidate -> Collections.singleton(new DomGotoRelatedItem(DomUtil.getDomElement(candidate.pointer.getElement()), "DevKit"));

  private static final NullableFunction<PointableCandidate, String> EXTENSION_NAMER =
    createNamer("line.marker.tooltip.extension.declaration", tag -> {
      final DomElement element = DomUtil.getDomElement(tag);
      if (!(element instanceof Extension)) return "?";
      return getExtensionPointName(((Extension)element).getExtensionPoint());
    });

  private static final NullableFunction<PointableCandidate, String> EXTENSION_POINT_NAMER =
    createNamer("line.marker.tooltip.extension.point.declaration", tag -> {
      return getExtensionPointName(DomUtil.getDomElement(tag));
    });

  private static @NotNull String getExtensionPointName(DomElement element) {
    if (!(element instanceof ExtensionPoint)) return "?";
    return ((ExtensionPoint)element).getEffectiveQualifiedName();
  }

  private static final NullableFunction<PointableCandidate, String> LISTENER_IMPLEMENTATION_NAMER =
    createNamer("line.marker.tooltip.listener.declaration", tag -> {
      final DomElement element = DomUtil.getDomElement(tag);
      if (!(element instanceof Listeners.Listener)) return "?";
      return StringUtil.notNullize(((Listeners.Listener)element).getListenerClassName().getStringValue(), "?");
    });

  private static final NullableFunction<PointableCandidate, String> LISTENER_TOPIC_NAMER =
    createNamer("line.marker.tooltip.listener.declaration", tag -> {
      final DomElement element = DomUtil.getDomElement(tag);
      if (!(element instanceof Listeners.Listener)) return "?";
      return StringUtil.notNullize(((Listeners.Listener)element).getTopicClassName().getStringValue(), "?");
    });

  private static final NotNullFunction<XmlTag, @NlsContexts.ListItem String> ACTION_OR_GROUP_NAME_FUNCTION = tag -> {
    final DomElement element = DomUtil.getDomElement(tag);
    if (!(element instanceof ActionOrGroup)) return "?";
    return StringUtil.notNullize(((ActionOrGroup)element).getId().getStringValue(), "?");
  };

  private static final NullableFunction<PointableCandidate, String> ACTION_NAMER =
    createNamer("line.marker.tooltip.action.declaration", ACTION_OR_GROUP_NAME_FUNCTION);

  private static final NullableFunction<PointableCandidate, String> ACTION_GROUP_NAMER =
    createNamer("line.marker.tooltip.action.group.declaration", ACTION_OR_GROUP_NAME_FUNCTION);

  private static final NullableFunction<PointableCandidate, String> COMPONENT_NAMER =
    createNamer("line.marker.tooltip.component.declaration", tag -> {
      final DomElement element = DomUtil.getDomElement(tag);
      if (!(element instanceof Component)) return "?";
      Component component = (Component)element;
      return StringUtil.notNullize(component.getImplementationClass().getStringValue(), "?");
    });

  private LineMarkerInfoHelper() {
  }

  static @NotNull RelatedItemLineMarkerInfo<PsiElement> createExtensionLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                                      @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.extension"),
                                      EXTENSION_NAMER);
  }

  static @NotNull RelatedItemLineMarkerInfo<PsiElement> createExtensionPointLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                                           @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.extension.point"),
                                      EXTENSION_POINT_NAMER);
  }

  static RelatedItemLineMarkerInfo<PsiElement> createListenerLineMarkerInfo(@NotNull List<? extends ListenerCandidate> targets,
                                                                            @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.listener"),
                                      LISTENER_TOPIC_NAMER);
  }

  static RelatedItemLineMarkerInfo<PsiElement> createListenerTopicLineMarkerInfo(@NotNull List<? extends ListenerCandidate> targets,
                                                                            @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.listener"),
                                      LISTENER_IMPLEMENTATION_NAMER);
  }

  static RelatedItemLineMarkerInfo<?> createActionLineMarkerInfo(List<? extends ActionCandidate> targets, PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.action"),
                                      ACTION_NAMER);
  }

  static RelatedItemLineMarkerInfo<?> createActionGroupLineMarkerInfo(List<? extends ActionCandidate> targets, PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.action.group"),
                                      ACTION_GROUP_NAMER);
  }

  static RelatedItemLineMarkerInfo<?> createComponentLineMarkerInfo(List<? extends ComponentCandidate> targets, PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.component"),
                                      COMPONENT_NAMER);
  }

  private static @NotNull RelatedItemLineMarkerInfo<PsiElement> createPluginLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                                           @NotNull PsiElement element,
                                                                                           @Nls(capitalization = Nls.Capitalization.Title) String popup,
                                                                                           NullableFunction<PointableCandidate, String> namer) {
    return NavigationGutterIconBuilder
      .create(DevKitIcons.Gutter.Plugin, CONVERTER, RELATED_ITEM_PROVIDER)
      .setTargets(targets)
      .setPopupTitle(popup)
      .setNamer(namer)
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(element);
  }

  private static @NotNull NullableFunction<PointableCandidate, String> createNamer(@PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String tooltipPatternPropertyName,
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
        fileDisplayName += new HtmlBuilder()
          .append(" ")
          .append(HtmlChunk.text("[" + module.getName() + "]")
                    .wrapWith(HtmlChunk.font(ColorUtil.toHex(UIUtil.getInactiveTextColor()))));
      }

      return DevKitBundle.message(tooltipPatternPropertyName,
                                  path, String.valueOf(tag.getTextRange().getStartOffset()), nameProvider.fun(tag),
                                  fileDisplayName);
    };
  }
}
