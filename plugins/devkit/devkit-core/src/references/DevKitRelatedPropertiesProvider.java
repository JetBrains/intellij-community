// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.navigation.DevkitRelatedLineMarkerProviderBase;
import org.jetbrains.idea.devkit.util.DescriptorI18nUtil;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class DevKitRelatedPropertiesProvider extends DevkitRelatedLineMarkerProviderBase {

  @Override
  public String getName() {
    return DevKitBundle.message("line.marker.related.property.description");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Properties;
  }

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement leaf, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (!(leaf instanceof XmlToken)) return;
    if (leaf.getNode().getElementType() != XmlTokenType.XML_NAME) return;
    PsiElement prev = PsiTreeUtil.getPrevSiblingOfType(leaf, XmlToken.class);
    if (prev == null || prev.getNode().getElementType() != XmlTokenType.XML_START_TAG_START) return;
    if (!leaf.textMatches("action") && !leaf.textMatches("group")) return;

    DomElement ag = DomUtil.getDomElement(leaf);

    if (!(ag instanceof ActionOrGroup)) return;

    GenericAttributeValue<String> attr = ((ActionOrGroup)ag).getId();
    String id = attr.getStringValue();
    String tagName = ag.getXmlElementName();
    if (!"action".equals(tagName) && !"group".equals(tagName)) {
      return;
    }

    if (id != null) {
      PropertiesFile file = DescriptorI18nUtil.findBundlePropertiesFile(ag);

      if (file == null) return;

      JBIterable<PsiElement> targets = JBIterable.of(
        file.findPropertyByKey(tagName + "." + id + ".text"),
        file.findPropertyByKey(tagName + "." + id + ".description"))
        .filter(PsiElement.class);

      if (targets.isEmpty()) return;

      result.add(
        NavigationGutterIconBuilder.create(AllIcons.FileTypes.Properties,
                                           e -> Collections.singletonList(((PsiElement)e)),
                                           e -> Collections.singletonList(new GotoRelatedItem((PsiElement)e)))
          .setTargets(targets.toList())
          .setTooltipText(DevKitBundle.message("line.marker.related.property.title"))
          .setAlignment(GutterIconRenderer.Alignment.RIGHT)
          .createLineMarkerInfo(leaf)
      );
    }
  }
}
