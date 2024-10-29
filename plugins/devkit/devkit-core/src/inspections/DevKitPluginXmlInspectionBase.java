// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.RemoveDomElementQuickFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DevKitDomUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 * Check {@link #isAllowed} from {@link #checkDomElement} to skip non-relevant files.
 * Do NOT invoke {@code super.checkDomElement()}.
 */
public abstract class DevKitPluginXmlInspectionBase extends BasicDomElementsInspection<IdeaPlugin> {

  protected DevKitPluginXmlInspectionBase() {
    super(IdeaPlugin.class);
  }

  protected boolean isAllowed(@NotNull DomElementAnnotationHolder holder) {
    return DevKitInspectionUtil.isAllowed(holder.getFileElement().getFile());
  }

  protected static boolean hasMissingAttribute(DomElement element, @NonNls String attributeName) {
    final GenericAttributeValue<?> attribute = DevKitDomUtil.getAttribute(element, attributeName);
    return attribute != null && !DomUtil.hasXml(attribute);
  }

  protected static void highlightRedundant(DomElement element,
                                           @InspectionMessage String message,
                                           ProblemHighlightType highlightType,
                                           DomElementAnnotationHolder holder) {
    holder.createProblem(element, highlightType, message, null, new RemoveDomElementQuickFix(element)).highlightWholeElement();
  }

  protected static boolean isUnderProductionSources(DomElement domElement, @NotNull Module module) {
    VirtualFile virtualFile = DomUtil.getFile(domElement).getVirtualFile();
    return virtualFile != null &&
           ModuleRootManager.getInstance(module).getFileIndex().isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.PRODUCTION);
  }

  @Override
  public final boolean isDumbAware() {
    return false;
  }
}
