// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.highlighting.AddDomElementQuickFix;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ApplicationComponents;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.Group;
import org.jetbrains.idea.devkit.dom.ModuleComponents;
import org.jetbrains.idea.devkit.dom.ProjectComponents;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

@VisibleForTesting
@ApiStatus.Internal
public final class PluginXmlDynamicPluginInspection extends DevKitPluginXmlInspectionBase {
  public boolean highlightNonDynamicEPUsages = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("highlightNonDynamicEPUsages", DevKitBundle.message("inspections.plugin.xml.dynamic.plugin.option.highlight.usages.ep")));
  }

  @Override
  protected void checkDomElement(@NotNull DomElement element,
                                 @NotNull DomElementAnnotationHolder holder,
                                 @NotNull DomHighlightingHelper helper) {
    if (!isAllowed(holder)) return;

    if (element instanceof ApplicationComponents ||
        element instanceof ProjectComponents ||
        element instanceof ModuleComponents) {
      highlightComponents(holder, element);
    }

    else if (element instanceof ExtensionPoint) {
      highlightExtensionPoint(holder, ((ExtensionPoint)element));
    }

    else if (element instanceof Group) {
      highlightGroup(holder, (Group)element);
    }

    else if (highlightNonDynamicEPUsages && element instanceof Extension) {
      highlightExtension(holder, ((Extension)element));
    }
  }

  private static void highlightComponents(DomElementAnnotationHolder holder, DomElement component) {
    holder.createProblem(component,
                         new HtmlBuilder()
                           .append(DevKitBundle.message("inspections.plugin.xml.dynamic.plugin.component.usage"))
                           .nbsp()
                           .append(HtmlChunk
                                     .link(
                                       "https://plugins.jetbrains.com/docs/intellij/plugin-components.html?from=DevkitPluginXmlDynamicInspection",
                                       DevKitBundle.message("inspections.plugin.xml.dynamic.plugin.component.usage.docs.link.title")))
                           .wrapWithHtmlBody()
                           .toString());
  }

  private static void highlightExtensionPoint(DomElementAnnotationHolder holder, ExtensionPoint extensionPoint) {
    if (!DomUtil.hasXml(extensionPoint.getDynamic())) {
      holder.createProblem(extensionPoint,
                           DevKitBundle.message("inspections.plugin.xml.dynamic.plugin.extension.point",
                                                extensionPoint.getEffectiveQualifiedName()));
    }
  }

  private static void highlightGroup(DomElementAnnotationHolder holder, Group group) {
    if (!DomUtil.hasXml(group.getId())) {
      holder.createProblem(group, DevKitBundle.message("inspections.plugin.xml.dynamic.plugin.id.required.for.group"),
                           new AddDomElementQuickFix<>(group.getId()));
    }
  }

  private static void highlightExtension(DomElementAnnotationHolder holder, Extension extension) {
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();

    if (extensionPoint != null && Boolean.TRUE != extensionPoint.getDynamic().getValue()) {
      holder.createProblem(extension,
                           DevKitBundle.message("inspections.plugin.xml.dynamic.plugin.usage.of.non.dynamic.extension.point",
                                                extensionPoint.getEffectiveQualifiedName()));
    }
  }
}
