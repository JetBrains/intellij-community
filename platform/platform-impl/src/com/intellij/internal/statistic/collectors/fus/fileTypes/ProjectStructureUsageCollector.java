// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author gregsh
 */
final class ProjectStructureUsageCollector extends ProjectUsagesCollector {
  private final EventLogGroup GROUP = new EventLogGroup("project.structure", 4);

  private final EventId1<Integer> MODULES_TOTAL = GROUP.registerEvent("modules.total", EventFields.Count);
  private final EventId1<Integer> CONTENT_ROOTS_TOTAL = GROUP.registerEvent("content.roots.total", EventFields.Count);
  private final EventId1<Integer> SOURCE_ROOTS_TOTAL = GROUP.registerEvent("source.roots.total", EventFields.Count);
  private final EventId1<Integer> EXCLUDED_ROOTS_TOTAL = GROUP.registerEvent("excluded.roots.total", EventFields.Count);
  private final EventId2<Integer, String> SOURCE_ROOT = GROUP.registerEvent(
    "source.root", EventFields.Count,
    EventFields.String("type", Arrays.asList("cookbooks-root",
                                             "java-resource",
                                             "java-source",
                                             "java-test-resource",
                                             "java-test",
                                             "kotlin-resource",
                                             "kotlin-source",
                                             "kotlin-test-resource",
                                             "kotlin-test")));
  private final EventId1<Integer> PACKAGE_PREFIX = GROUP.registerEvent("package.prefix", EventFields.Count);
  private final EventId1<Integer> NAMED_SCOPES_TOTAL_LOCAL = GROUP.registerEvent("named.scopes.total.local", EventFields.Count);
  private final EventId1<Integer> NAMED_SCOPES_TOTAL_SHARED = GROUP.registerEvent("named.scopes.total.shared", EventFields.Count);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
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
    result.add(MODULES_TOTAL.metric(modules.length));
    result.add(CONTENT_ROOTS_TOTAL.metric(contentRoots));
    result.add(SOURCE_ROOTS_TOTAL.metric(sourceRoots));
    result.add(EXCLUDED_ROOTS_TOTAL.metric(excludedRoots));
    ObjectIterator<Object2IntMap.Entry<String>> iterator = types.object2IntEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Object2IntMap.Entry<String> entry = iterator.next();
      result.add(SOURCE_ROOT.metric(entry.getIntValue(), entry.getKey()));
    }
    if (PlatformUtils.isIntelliJ()) {
      result.add(PACKAGE_PREFIX.metric(packagePrefix));
    }

    NamedScope[] localScopes = NamedScopeManager.getInstance(project).getEditableScopes();
    result.add(NAMED_SCOPES_TOTAL_LOCAL.metric(localScopes.length));
    NamedScope[] sharedScopes = DependencyValidationManager.getInstance(project).getEditableScopes();
    result.add(NAMED_SCOPES_TOTAL_SHARED.metric(sharedScopes.length));

    return result;
  }
}
