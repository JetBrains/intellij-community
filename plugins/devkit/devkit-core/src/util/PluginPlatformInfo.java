// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;

/**
 * Resolves current target platform.
 */
public class PluginPlatformInfo {

  private static final PluginPlatformInfo UNRESOLVED_INSTANCE = new PluginPlatformInfo(PlatformResolveStatus.UNRESOLVED, null, null);

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

  /**
   * @return non-null only for {@link PlatformResolveStatus#DEVKIT}
   */
  public IdeaPlugin getMainIdeaPlugin() {
    return myMainIdeaPlugin;
  }

  @Nullable
  public BuildNumber getSinceBuildNumber() {
    return mySinceBuildNumber;
  }

  public static PluginPlatformInfo forDomElement(@NotNull DomElement pluginXmlDomElement) {
    Module module = pluginXmlDomElement.getModule();
    if (module == null) {
      return UNRESOLVED_INSTANCE;
    }

    boolean isDevkitModule = PluginModuleType.isPluginModuleOrDependency(module);
    if (!isDevkitModule) {
      return forModule(module);
    }

    IdeaPlugin plugin = DomUtil.getParentOfType(pluginXmlDomElement, IdeaPlugin.class, true);
    assert plugin != null;

    if (!plugin.hasRealPluginId()) {
      final XmlFile mainPluginXml = PluginModuleType.getPluginXml(module);
      if (mainPluginXml == null) {
        return new PluginPlatformInfo(PlatformResolveStatus.DEVKIT_NO_MAIN, null, null);
      }

      plugin = DescriptorUtil.getIdeaPlugin(mainPluginXml);
      assert plugin != null;
    }

    final GenericAttributeValue<BuildNumber> sinceBuild = plugin.getIdeaVersion().getSinceBuild();
    if (!DomUtil.hasXml(sinceBuild) ||
        sinceBuild.getValue() == null) {
      return new PluginPlatformInfo(PlatformResolveStatus.DEVKIT_NO_SINCE_BUILD, plugin, null);
    }

    return new PluginPlatformInfo(PlatformResolveStatus.DEVKIT, plugin, sinceBuild.getValue());
  }

  public static PluginPlatformInfo forModule(@NotNull Module module) {
    return CachedValuesManager.getManager(module.getProject())
      .getCachedValue(module, () ->
        CachedValueProvider.Result.createSingleDependency(_forModule(module), ProjectRootManager.getInstance(module.getProject())));
  }

  private static PluginPlatformInfo _forModule(@NotNull Module module) {
    final PsiClass markerClass = JavaPsiFacade.getInstance(module.getProject())
      .findClass(JBList.class.getName(), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false));
    if (markerClass == null) {
      return UNRESOLVED_INSTANCE;
    }

    final OrderEntry entry = LibraryUtil.findLibraryEntry(markerClass.getContainingFile().getVirtualFile(), module.getProject());
    if ((!(entry instanceof LibraryOrderEntry))) {
      return UNRESOLVED_INSTANCE;
    }

    final String libraryName = entry.getPresentableName();

    String versionSuffix = StringUtil.substringAfterLast(libraryName, ":20");
    if (versionSuffix == null ||
        !StringUtil.containsChar(versionSuffix, '.')) {
      return UNRESOLVED_INSTANCE;
    }
    versionSuffix = StringUtil.replace(versionSuffix, ".", "");
    if (versionSuffix.length() < 3) {
      return UNRESOLVED_INSTANCE;
    }

    final String branch =  versionSuffix.substring(0, 3);
    final BuildNumber number = BuildNumber.fromStringOrNull(branch);
    if (number == null) {
      return UNRESOLVED_INSTANCE;
    }

    return new PluginPlatformInfo(PlatformResolveStatus.GRADLE, null, number);
  }

  public enum PlatformResolveStatus {
    UNRESOLVED,
    DEVKIT,
    DEVKIT_NO_MAIN,
    DEVKIT_NO_SINCE_BUILD,
    GRADLE
  }
}