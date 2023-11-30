// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.properties.BundleNameEvaluator;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.*;

import java.util.List;

public final class DescriptorI18nUtil {

  @NonNls
  public static final String CORE_ACTIONS_BUNDLE = "messages.ActionsBundle";

  public static @Nullable PropertiesFile findBundlePropertiesFile(@Nullable DomElement domElement) {
    XmlElement bundleXmlElement = null;

    Actions actions = null;
    if (domElement instanceof ActionOrGroup ||
        domElement instanceof Separator ||
        domElement instanceof OverrideText ||
        domElement instanceof Synonym) {
      actions = DomUtil.getParentOfType(domElement, Actions.class, true);
      if (actions == null) return null;

      bundleXmlElement = DomUtil.hasXml(actions.getResourceBundle()) ? actions.getResourceBundle().getXmlAttributeValue() : null;
    }

    if (bundleXmlElement == null) {
      final IdeaPlugin ideaPlugin = DomUtil.getParentOfType(domElement, IdeaPlugin.class, true);
      if (ideaPlugin == null) return null;

      bundleXmlElement = DomUtil.hasXml(ideaPlugin.getResourceBundle()) ? ideaPlugin.getResourceBundle().getXmlElement() : null;
    }

    if (bundleXmlElement == null) {
      return findCoreActionsBundlePropertiesFile(actions);
    }

    final ResourceBundleReference bundleReference =
      ContainerUtil.findInstance(bundleXmlElement.getReferences(), ResourceBundleReference.class);
    if (bundleReference == null) return null;

    return ObjectUtils.tryCast(bundleReference.resolve(), PropertiesFile.class);
  }

  public static boolean canFallbackToCoreActionsBundle(@Nullable Actions actions) {
    if (actions == null) return false;

    final Module module = actions.getModule();
    if (module == null) return false;

    if (PsiUtil.isIdeaProject(module.getProject()) &&
        (module.getName().startsWith("intellij.platform.") || ApplicationManager.getApplication().isUnitTestMode())) {
      return true;
    }

    final IdeaPlugin ideaPlugin = DomUtil.getParentOfType(actions, IdeaPlugin.class, true);
    if (ideaPlugin == null) return false;

    return PluginManagerCore.CORE_PLUGIN_ID.equals(ideaPlugin.getPluginId()) ||
           !ideaPlugin.hasRealPluginId();
  }

  private static @Nullable PropertiesFile findCoreActionsBundlePropertiesFile(@Nullable Actions actions) {
    if (!canFallbackToCoreActionsBundle(actions)) return null;

    final Module module = actions.getModule();
    assert module != null;
    final Project project = module.getProject();

    final PropertiesReferenceManager propertiesReferenceManager = PropertiesReferenceManager.getInstance(project);
    List<PropertiesFile> actionsBundleFiles;

    Module resourcesModule = ApplicationManager.getApplication().isUnitTestMode() ? module :
                             ModuleManager.getInstance(project).findModuleByName("intellij.platform.resources.en");
    // not in IDEA project -> search in library
    if (resourcesModule == null) {
      actionsBundleFiles = propertiesReferenceManager.findPropertiesFiles(actions.getResolveScope(), CORE_ACTIONS_BUNDLE,
                                                                          BundleNameEvaluator.DEFAULT);
    }
    else {
      actionsBundleFiles = propertiesReferenceManager.findPropertiesFiles(resourcesModule, CORE_ACTIONS_BUNDLE);
    }
    return ContainerUtil.getOnlyItem(actionsBundleFiles);
  }
}
