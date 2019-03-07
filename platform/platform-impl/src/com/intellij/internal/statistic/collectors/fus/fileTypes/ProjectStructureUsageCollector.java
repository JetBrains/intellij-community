// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.JBIterable;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getCountingUsage;

/**
 * @author gregsh
 */
public class ProjectStructureUsageCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    Map<? extends JpsModuleSourceRootType<?>, String> typeNames = JBIterable.from(JpsModelSerializerExtension.getExtensions())
      .filter(o -> PluginInfoDetectorKt.getPluginInfo(o.getClass()).isDevelopedByJetBrains())
      .flatMap(JpsModelSerializerExtension::getModuleSourceRootPropertiesSerializers)
      .toMap(JpsElementPropertiesSerializer::getType, JpsElementPropertiesSerializer::getTypeId);
    boolean packagePrefixUsed = false;

    int contentRoots = 0, sourceRoots = 0, excludedRoots = 0;
    TObjectIntHashMap<String> types = new TObjectIntHashMap<>();
    Module[] modules = ModuleManager.getInstance(project).getModules();

    for (Module module : modules) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      contentRoots += rootManager.getContentEntries().length;
      sourceRoots += rootManager.getSourceRoots(true).length;
      excludedRoots += rootManager.getExcludeRoots().length;
      for (ContentEntry entry : rootManager.getContentEntries()) {
        for (SourceFolder source : entry.getSourceFolders()) {
          packagePrefixUsed = packagePrefixUsed || StringUtil.isNotEmpty(source.getPackagePrefix());
          String key = typeNames.get(source.getRootType());
          if (key == null) continue;
          if (!types.increment(key)) {
            types.put(key, 1);
          }
        }
      }
    }
    Set<UsageDescriptor> result = new HashSet<>();
    result.add(getCountingUsage("modules.count", modules.length));
    result.add(getCountingUsage("content.roots.count", contentRoots));
    result.add(getCountingUsage("source.roots.count", sourceRoots));
    result.add(getCountingUsage("excluded.roots.count", excludedRoots));
    types.forEachEntry((key, count) -> result.add(getCountingUsage("source.root." + key, count)));
    if (PlatformUtils.isIntelliJ()) {
      result.add(new UsageDescriptor(packagePrefixUsed ? "package.prefix.used" : "package.prefix.not.used"));
    }
    return result;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "project.structure";
  }
}
