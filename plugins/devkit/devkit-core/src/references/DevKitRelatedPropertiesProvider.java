// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.DevKitIcons;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.OverrideText;
import org.jetbrains.idea.devkit.navigation.DevkitRelatedLineMarkerProviderBase;
import org.jetbrains.idea.devkit.util.DescriptorI18nUtil;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * Provides gutter icon/goto related navigation for implicit message keys in {@code plugin.xml}.
 * <p>
 * The following elements are supported:
 * <ul>
 *   <li>{@code <plugin> -> <id>}: {@code plugin.@id.description}</li>
 *   <li>{@code <action>|<group>}: {@code action.@id.text|description}</li>
 *   <li>{@code <override-text>}: {@code action.actionId.@place.text}</li>
 * </ul>
 * </p>
 *
 * @see MessageBundleReferenceContributor
 */
final class DevKitRelatedPropertiesProvider extends DevkitRelatedLineMarkerProviderBase {

  @Override
  public String getName() {
    return DevKitBundle.message("line.marker.related.property.description");
  }

  @Override
  public @NotNull Icon getIcon() {
    return DevKitIcons.Gutter.Properties;
  }

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement leaf, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (!(leaf instanceof XmlToken)) return;
    if (leaf.getNode().getElementType() != XmlTokenType.XML_NAME) return;
    PsiElement prev = PsiTreeUtil.getPrevSiblingOfType(leaf, XmlToken.class);
    if (prev == null || prev.getNode().getElementType() != XmlTokenType.XML_START_TAG_START) return;
    if (!leaf.textMatches("action") && !leaf.textMatches("group") &&
        !leaf.textMatches("override-text") &&
        !leaf.textMatches("id")) {
      return;
    }

    DomElement domElement = DomUtil.getDomElement(leaf);
    if (domElement instanceof ActionOrGroup) {
      ActionOrGroup actionOrGroup = (ActionOrGroup)domElement;
      createLineMarker(leaf, result, domElement, actionOrGroup.getId());
    }
    else if (domElement instanceof OverrideText) {
      OverrideText overrideText = (OverrideText)domElement;
      createLineMarker(leaf, result, domElement, overrideText.getPlace());
    }
    else if (domElement instanceof GenericDomValue) {
      createLineMarker(leaf, result, domElement, (GenericDomValue<?>)domElement);
    }
  }

  private static void createLineMarker(@NotNull PsiElement leaf,
                                       @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                       DomElement domElement,
                                       GenericDomValue<?> referenceElement) {
    if (!DomUtil.hasXml(referenceElement)) return;
    final XmlElement valueXmlElement = DomUtil.getValueElement(referenceElement);
    if (valueXmlElement == null) return;

    PropertiesFile file = DescriptorI18nUtil.findBundlePropertiesFile(domElement);
    if (file == null) return;

    final Query<PsiReference> query = ReferencesSearch.search(valueXmlElement, new LocalSearchScope(file.getContainingFile()));
    if (query.findFirst() == null) return;

    result.add(
      NavigationGutterIconBuilder.create(DevKitIcons.Gutter.Properties,
                                         e -> Collections.singletonList(((PsiElement)e)),
                                         e -> Collections.singletonList(new GotoRelatedItem((PsiElement)e)))
        .setTargets(NotNullLazyValue.createValue(() -> {
          return ContainerUtil.map(query.findAll(),
                                   reference -> PsiTreeUtil.getParentOfType(reference.getElement(), Property.class));
        }))
        .setPopupTitle(DevKitBundle.message("line.marker.related.property.popup.title"))
        .setTooltipText(DevKitBundle.message("line.marker.related.property.tooltip"))
        .setAlignment(GutterIconRenderer.Alignment.RIGHT)
        .createLineMarkerInfo(leaf)
    );
  }
}
