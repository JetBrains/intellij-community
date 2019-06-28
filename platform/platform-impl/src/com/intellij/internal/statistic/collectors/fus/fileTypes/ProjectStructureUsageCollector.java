// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
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

import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newCounterMetric;

/**
 * @author gregsh
 */
public class ProjectStructureUsageCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "project.structure";
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    Map<? extends JpsModuleSourceRootType<?>, String> typeNames = JBIterable.from(JpsModelSerializerExtension.getExtensions())
      .filter(o -> PluginInfoDetectorKt.getPluginInfo(o.getClass()).isDevelopedByJetBrains())
      .flatMap(JpsModelSerializerExtension::getModuleSourceRootPropertiesSerializers)
      .toMap(JpsElementPropertiesSerializer::getType, JpsElementPropertiesSerializer::getTypeId);
    int contentRoots = 0, sourceRoots = 0, excludedRoots = 0, packagePrefix = 0;
    TObjectIntHashMap<String> types = new TObjectIntHashMap<>();
    Module[] modules = ModuleManager.getInstance(project).getModules();

    for (Module module : modules) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      contentRoots += rootManager.getContentEntries().length;
      sourceRoots += rootManager.getSourceRoots(true).length;
      excludedRoots += rootManager.getExcludeRoots().length;
      for (ContentEntry entry : rootManager.getContentEntries()) {
        for (SourceFolder source : entry.getSourceFolders()) {
          if (StringUtil.isNotEmpty(source.getPackagePrefix())) {
            packagePrefix++;
          }
          String key = typeNames.get(source.getRootType());
          if (key == null) continue;
          if (!types.increment(key)) {
            types.put(key, 1);
          }
        }
      }
    }

    final Set<MetricEvent> result = new HashSet<>();
    result.add(newCounterMetric("modules.total", modules.length));
    result.add(newCounterMetric("content.roots.total", contentRoots));
    result.add(newCounterMetric("source.roots.total", sourceRoots));
    result.add(newCounterMetric("excluded.roots.total", excludedRoots));
    types.forEachEntry(
      (key, count) -> result.add(newCounterMetric("source.root", count, new FeatureUsageData().addData("type", key)))
    );
    if (PlatformUtils.isIntelliJ()) {
      result.add(newCounterMetric("package.prefix", packagePrefix));
    }
    return result;
  }
}
