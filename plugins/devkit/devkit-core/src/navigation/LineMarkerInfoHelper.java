// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.DomGotoRelatedItem;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.NotNullFunction;
import com.intellij.util.NullableFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.dom.*;
import org.jetbrains.idea.devkit.util.ActionCandidate;
import org.jetbrains.idea.devkit.util.ComponentCandidate;
import org.jetbrains.idea.devkit.util.ListenerCandidate;
import org.jetbrains.idea.devkit.util.PointableCandidate;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class LineMarkerInfoHelper {

  private LineMarkerInfoHelper() {
  }

  private static final NotNullFunction<PointableCandidate, Collection<? extends PsiElement>> CONVERTER =
    candidate -> ContainerUtil.createMaybeSingletonList(candidate.pointer.getElement());

  private static @NotNull String getExtensionPointName(DomElement element) {
    if (!(element instanceof ExtensionPoint)) return "?";
    return ((ExtensionPoint)element).getEffectiveQualifiedName();
  }

  static @NotNull RelatedItemLineMarkerInfo<PsiElement> createExtensionLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                                      @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.extension"),
                                      (NullableFunction<Extension, String>)extension ->
                                        getExtensionPointName(extension.getExtensionPoint()));
  }

  static @NotNull RelatedItemLineMarkerInfo<PsiElement> createExtensionPointLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                                                                                           @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.extension.point"),
                                      (NullableFunction<ExtensionPoint, String>)extensionPoint -> getExtensionPointName(extensionPoint));
  }

  static RelatedItemLineMarkerInfo<PsiElement> createListenerLineMarkerInfo(@NotNull List<? extends ListenerCandidate> targets,
                                                                            @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.listener"),
                                      (NullableFunction<Listeners.Listener, String>)listener -> listener.getTopicClassName()
                                        .getStringValue());
  }

  static RelatedItemLineMarkerInfo<PsiElement> createListenerTopicLineMarkerInfo(@NotNull List<? extends ListenerCandidate> targets,
                                                                                 @NotNull PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.listener"),
                                      (NullableFunction<Listeners.Listener, String>)listener -> listener.getListenerClassName()
                                        .getStringValue());
  }

  static RelatedItemLineMarkerInfo<?> createActionLineMarkerInfo(List<? extends ActionCandidate> targets, PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.action"),
                                      (NullableFunction<ActionOrGroup, String>)actionOrGroup -> actionOrGroup.getId().getStringValue());
  }

  static RelatedItemLineMarkerInfo<?> createActionGroupLineMarkerInfo(List<? extends ActionCandidate> targets, PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.action.group"),
                                      (NullableFunction<ActionOrGroup, String>)actionOrGroup -> actionOrGroup.getId().getStringValue());
  }

  static RelatedItemLineMarkerInfo<?> createComponentLineMarkerInfo(List<? extends ComponentCandidate> targets, PsiElement element) {
    return createPluginLineMarkerInfo(targets, element,
                                      DevKitBundle.message("gutter.related.navigation.choose.component"),
                                      (NullableFunction<Component, String>)component -> component.getImplementationClass()
                                        .getStringValue());
  }


  private static <T extends DomElement> @NotNull RelatedItemLineMarkerInfo<PsiElement>
  createPluginLineMarkerInfo(@NotNull List<? extends PointableCandidate> targets,
                             @NotNull PsiElement element,
                             @Nls(capitalization = Nls.Capitalization.Title) String popup,
                             NullableFunction<T, @NlsSafe String> namer) {
    return NavigationGutterIconBuilder
      .create(DevKitIcons.Gutter.Plugin, CONVERTER, target -> {
        DomElement domElement = DomUtil.getDomElement(target.pointer.getElement());
        return Collections.singletonList(new DomGotoRelatedItem(domElement, "DevKit") {
          @Override
          public String getCustomName() {
            //noinspection unchecked
            return getDomElementName((T)domElement, namer);
          }

          @Nls
          @Override
          public @Nullable String getCustomContainerName() {
            PsiElement psiElement = getElement();
            if (psiElement == null) return null;
            return UniqueVFilePathBuilder.getInstance()
              .getUniqueVirtualFilePath(psiElement.getProject(), psiElement.getContainingFile().getVirtualFile());
          }
        });
      })
      .setTargets(targets)
      .setPopupTitle(popup)
      .setNamer(candidate -> {
        DomElement domElement = DomUtil.getDomElement(candidate.pointer.getElement());
        //noinspection unchecked
        return getDomElementName((T)domElement, namer);
      })
      .setCellRenderer(new Computable<>() {
        @Override
        public PsiElementListCellRenderer<?> compute() {
          return new PsiElementListCellRenderer<>() {

            @Override
            protected Icon getIcon(PsiElement element) {
              DomElement domElement = DomUtil.getDomElement(element);
              assert domElement != null;
              return ObjectUtils.chooseNotNull(domElement.getPresentation().getIcon(), element.getIcon(getIconFlags()));
            }

            @Override
            public String getElementText(PsiElement element) {
              DomElement domElement = DomUtil.getDomElement(element);
              //noinspection unchecked
              return getDomElementName((T)domElement, namer);
            }

            @Override
            protected String getContainerText(PsiElement element, String name) {
              return UniqueVFilePathBuilder.getInstance()
                .getUniqueVirtualFilePath(element.getProject(), element.getContainingFile().getVirtualFile());
            }
          };
        }
      })
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(element);
  }

  @NlsSafe
  private static <T extends DomElement> String getDomElementName(T domElement, NullableFunction<T, @NlsSafe String> namer) {
    return StringUtil.defaultIfEmpty(namer.fun(domElement), "?");
  }

}
