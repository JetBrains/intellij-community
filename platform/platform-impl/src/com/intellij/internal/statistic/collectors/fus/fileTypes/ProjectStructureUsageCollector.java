// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
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
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.JBIterable;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author gregsh
 */
final class ProjectStructureUsageCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "project.structure";
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    Map<? extends JpsModuleSourceRootType<?>, String> typeNames = JBIterable.from(JpsModelSerializerExtension.getExtensions())
      .filter(o -> PluginInfoDetectorKt.getPluginInfo(o.getClass()).isDevelopedByJetBrains())
      .flatMap(JpsModelSerializerExtension::getModuleSourceRootPropertiesSerializers)
      .toMap(JpsElementPropertiesSerializer::getType, JpsElementPropertiesSerializer::getTypeId);
    int contentRoots = 0, sourceRoots = 0, excludedRoots = 0, packagePrefix = 0;
    Object2IntOpenHashMap<String> types = new Object2IntOpenHashMap<>();
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
          if (key == null) {
            continue;
          }
          types.addTo(key, 1);
        }
      }
    }

    final Set<MetricEvent> result = new HashSet<>();
    result.add(MetricEventFactoryKt.newCounterMetric("modules.total", modules.length));
    result.add(MetricEventFactoryKt.newCounterMetric("content.roots.total", contentRoots));
    result.add(MetricEventFactoryKt.newCounterMetric("source.roots.total", sourceRoots));
    result.add(MetricEventFactoryKt.newCounterMetric("excluded.roots.total", excludedRoots));
    ObjectIterator<Object2IntMap.Entry<String>> iterator = types.object2IntEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Object2IntMap.Entry<String> entry = iterator.next();
      result.add(MetricEventFactoryKt.newCounterMetric("source.root", entry.getIntValue(), new FeatureUsageData().addData("type", entry.getKey())));
    }
    if (PlatformUtils.isIntelliJ()) {
      result.add(MetricEventFactoryKt.newCounterMetric("package.prefix", packagePrefix));
    }

    NamedScope[] localScopes = NamedScopeManager.getInstance(project).getEditableScopes();
    result.add(MetricEventFactoryKt.newCounterMetric("named.scopes.total.local", localScopes.length));
    NamedScope[] sharedScopes = DependencyValidationManager.getInstance(project).getEditableScopes();
    result.add(MetricEventFactoryKt.newCounterMetric("named.scopes.total.shared", sharedScopes.length));

    return result;
  }
}
