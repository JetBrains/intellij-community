// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.impl.analysis.InsertRequiredAttributeFix;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.AddDomElementQuickFix;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/**
 * @author Dmitry Avdeev
 */
public class InspectionMappingConsistencyInspection extends DevKitPluginXmlInspectionBase {
  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (!(element instanceof Extension)) {
      return;
    }

    ExtensionPoint extensionPoint = ((Extension)element).getExtensionPoint();
    if (extensionPoint == null ||
        !InheritanceUtil.isInheritor(extensionPoint.getBeanClass().getValue(), InspectionEP.class.getName())) {
      return;
    }

    if (hasDefinedAttribute(element, "key")) {
      if (!hasDefinedAttribute(element, "bundle")) {
        checkDefaultBundle(element, holder);
      }
    }
    else if (!hasDefinedAttribute(element, "displayName")) {
      //noinspection DialogTitleCapitalization
      registerProblem(element, holder,
                      DevKitBundle.message("inspections.inspection.mapping.consistency.specify.displayName.or.key"),
                      "displayName", "key");
    }

    if (hasDefinedAttribute(element, "groupKey")) {
      if (!hasDefinedAttribute(element, "bundle") &&
          !hasDefinedAttribute(element, "groupBundle")) {
        checkDefaultBundle(element, holder);
      }
    }
    else if (!hasDefinedAttribute(element, "groupName")) {
      //noinspection DialogTitleCapitalization
      registerProblem(element, holder,
                      DevKitBundle.message("inspections.inspection.mapping.consistency.specify.groupName.or.groupKey"),
                      "groupName", "groupKey");
    }
  }

  private static boolean hasDefinedAttribute(DomElement element, @NonNls String attributeName) {
    final GenericAttributeValue attribute = DevKitPluginXmlInspectionBase.getAttribute(element, attributeName);
    return attribute != null && DomUtil.hasXml(attribute);
  }

  private static void checkDefaultBundle(DomElement element, DomElementAnnotationHolder holder) {
    IdeaPlugin plugin = DomUtil.getParentOfType(element, IdeaPlugin.class, true);
    if (plugin != null && !DomUtil.hasXml(plugin.getResourceBundle())) {
      holder.createProblem(element, DevKitBundle.message("inspections.inspection.mapping.consistency.specify.bundle"),
                           new AddDomElementQuickFix<>(plugin.getResourceBundle()));
    }
  }

  private static void registerProblem(DomElement element, DomElementAnnotationHolder holder,
                                      @InspectionMessage String message,
                                      @NonNls String... createAttrs) {
    if (holder.isOnTheFly()) {
      holder.createProblem(element, message, ContainerUtil.map(createAttrs, attributeName -> {
        final XmlTag tag = element.getXmlTag();
        assert tag != null;
        return new InsertRequiredAttributeFix(tag, attributeName) {
          @NotNull
          @Override
          public String getText() {
            return DevKitBundle.message("inspections.inspection.mapping.consistency.fix.insert.attribute", attributeName);
          }
        };
      }, new LocalQuickFix[createAttrs.length]));
    }
    else {
      holder.createProblem(element, message);
    }
  }
}
