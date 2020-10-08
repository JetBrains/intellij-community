// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import icons.DevkitIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.OverrideText;
import org.jetbrains.idea.devkit.navigation.DevkitRelatedLineMarkerProviderBase;
import org.jetbrains.idea.devkit.util.DescriptorI18nUtil;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

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
public class DevKitRelatedPropertiesProvider extends DevkitRelatedLineMarkerProviderBase {

  @Override
  public String getName() {
    return DevKitBundle.message("line.marker.related.property.description");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return DevkitIcons.Gutter.Properties;
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

    Collection<PsiElement> properties =
      ReferencesSearch.search(valueXmlElement, GlobalSearchScope.fileScope(file.getContainingFile()))
        .mapping((Function<PsiReference, PsiElement>)reference -> PsiTreeUtil.getParentOfType(reference.getElement(), Property.class))
        .findAll();
    if (properties.isEmpty()) return;

    result.add(
      NavigationGutterIconBuilder.create(DevkitIcons.Gutter.Properties,
                                         e -> Collections.singletonList(((PsiElement)e)),
                                         e -> Collections.singletonList(new GotoRelatedItem((PsiElement)e)))
        .setTargets(properties)
        .setPopupTitle(DevKitBundle.message("line.marker.related.property.popup.title"))
        .setTooltipText(DevKitBundle.message("line.marker.related.property.tooltip"))
        .setAlignment(GutterIconRenderer.Alignment.RIGHT)
        .createLineMarkerInfo(leaf)
    );
  }
}
