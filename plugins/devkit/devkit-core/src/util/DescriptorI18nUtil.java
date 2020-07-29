// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Actions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.Separator;

import java.util.List;

public class DescriptorI18nUtil {

  @NonNls
  public static final String CORE_ACTIONS_BUNDLE = "messages.ActionsBundle";

  public static @Nullable PropertiesFile findBundlePropertiesFile(@Nullable DomElement domElement) {
    XmlElement bundleXmlElement = null;

    Actions actions = null;
    if (domElement instanceof ActionOrGroup ||
        domElement instanceof Separator) {
      actions = DomUtil.getParentOfType(domElement, Actions.class, true);
      if (actions == null) return null;

      bundleXmlElement = actions.getResourceBundle().getXmlAttributeValue();
    }

    if (bundleXmlElement == null) {
      final IdeaPlugin ideaPlugin = DomUtil.getParentOfType(domElement, IdeaPlugin.class, true);
      if (ideaPlugin == null) return null;

      bundleXmlElement = ideaPlugin.getResourceBundle().getXmlElement();
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

    final IdeaPlugin ideaPlugin = DomUtil.getParentOfType(actions, IdeaPlugin.class, true);
    if (ideaPlugin == null) return false;

    return PluginManagerCore.CORE_PLUGIN_ID.equals(ideaPlugin.getPluginId()) ||
           PsiUtil.isIdeaProject(module.getProject());
  }

  private static @Nullable PropertiesFile findCoreActionsBundlePropertiesFile(@Nullable Actions actions) {
    if (!canFallbackToCoreActionsBundle(actions)) return null;

    final Module module = actions.getModule();
    assert module != null;
    final List<PropertiesFile> actionsBundleFiles =
      PropertiesReferenceManager.getInstance(module.getProject()).findPropertiesFiles(module, CORE_ACTIONS_BUNDLE);
    return ObjectUtils.tryCast(ContainerUtil.getOnlyItem(actionsBundleFiles), PropertiesFile.class);
  }
}
