// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;

/**
 *
 */
public class PluginPlatformInfo {

  private final PlatformResolveStatus myPlatformResolveStatus;
  private final IdeaPlugin myMainIdeaPlugin;
  private final BuildNumber mySinceBuildNumber;

  private PluginPlatformInfo(PlatformResolveStatus platformResolveStatus,
                             IdeaPlugin mainIdeaPlugin,
                             BuildNumber sinceBuildNumber) {
    myPlatformResolveStatus = platformResolveStatus;
    myMainIdeaPlugin = mainIdeaPlugin;
    mySinceBuildNumber = sinceBuildNumber;
  }

  public PlatformResolveStatus getResolveStatus() {
    return myPlatformResolveStatus;
  }

  public IdeaPlugin getMainIdeaPlugin() {
    return myMainIdeaPlugin;
  }

  public BuildNumber getSinceBuildNumber() {
    return mySinceBuildNumber;
  }

  public static PluginPlatformInfo forDomElement(DomElement pluginXmlDomElement) {
    Module module = pluginXmlDomElement.getModule();
    if (module == null) {
      return new PluginPlatformInfo(PlatformResolveStatus.UNRESOLVED, null, null);
    }
    boolean isDevkitModule = PluginModuleType.isPluginModuleOrDependency(module);

    IdeaPlugin plugin = DomUtil.getParentOfType(pluginXmlDomElement, IdeaPlugin.class, true);
    assert plugin != null;

    if (!plugin.hasRealPluginId()) {
      final XmlFile mainPluginXml = PluginModuleType.getPluginXml(module);
      if (mainPluginXml == null) {
        PlatformResolveStatus resolveStatus = isDevkitModule ? PlatformResolveStatus.DEVKIT_NO_MAIN : PlatformResolveStatus.UNRESOLVED;
        return new PluginPlatformInfo(resolveStatus, null, null);
      }

      plugin = DescriptorUtil.getIdeaPlugin(mainPluginXml);
      assert plugin != null;
    }

    final GenericAttributeValue<BuildNumber> sinceBuild = plugin.getIdeaVersion().getSinceBuild();
    if (!DomUtil.hasXml(sinceBuild) ||
        sinceBuild.getValue() == null) {
      PlatformResolveStatus resolveStatus = isDevkitModule ? PlatformResolveStatus.DEVKIT_NO_SINCE_BUILD : PlatformResolveStatus.OTHER;
      return new PluginPlatformInfo(resolveStatus, plugin, null);
    }

    return new PluginPlatformInfo(PlatformResolveStatus.DEVKIT, plugin, sinceBuild.getValue());
  }

  public enum PlatformResolveStatus {
    UNRESOLVED,
    DEVKIT,
    DEVKIT_NO_MAIN,
    DEVKIT_NO_SINCE_BUILD,
    OTHER
  }
}