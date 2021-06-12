// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.*;

public final class PluginIdDependenciesIndex extends PluginXmlIndexBase<String, Void> {
  private static final ID<String, Void> NAME = ID.create("PluginIdDependenciesIndex");

  @NonNls
  private static final String FILENAME_KEY_PREFIX = "___FILENAME___";

  @NonNls
  private static final String PLUGIN_ID_KEY_PREFIX = "___PLUGIN_ID___";

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataExternalizer<Void> getValueExternalizer() {
    return VoidDataExternalizer.INSTANCE;
  }

  @Override
  protected Map<String, Void> performIndexing(IdeaPlugin plugin) {
    List<String> ids = new SmartList<>();
    final String pluginId = plugin.getPluginId();
    if (pluginId != null) {
      ids.add(PLUGIN_ID_KEY_PREFIX + pluginId);
    }

    //noinspection unchecked
    final List<Dependency> dependencies = (List<Dependency>)getChildrenWithoutIncludes(plugin, "depends");
    for (Dependency dependency : dependencies) {
      ContainerUtil.addIfNotNull(ids, dependency.getStringValue());

      final String configFile = dependency.getConfigFile().getStringValue();
      if (configFile != null) {
        final String filename = PathUtil.getFileName(configFile);
        ids.add(getDependsIndexingKey(filename));
      }
    }

    return ContainerUtil.newHashMap(ids, Collections.nCopies(ids.size(), null));
  }

  @Override
  public int getVersion() {
    return 2;
  }

  public static Set<String> getPluginAndDependsIds(Project project, Set<VirtualFile> files) {
    Set<String> ids = new HashSet<>();
    for (VirtualFile file : files) {
      final Set<String> keys = FileBasedIndex.getInstance().getFileData(NAME, file, project).keySet();
      final String pluginId = findPluginId(keys);
      ContainerUtil.addIfNotNull(ids, pluginId);

      ids.addAll(ContainerUtil.filter(keys, s ->
        !StringUtil.startsWith(s, PLUGIN_ID_KEY_PREFIX) &&
        !StringUtil.startsWith(s, FILENAME_KEY_PREFIX)));
    }
    return ids;
  }

  @Nullable
  @NlsSafe
  public static String getPluginId(Project project, VirtualFile file) {
    final Set<String> keys = FileBasedIndex.getInstance().getFileData(NAME, file, project).keySet();
    return findPluginId(keys);
  }

  @Nullable
  private static String findPluginId(Set<String> data) {
    final String pluginIdEntry = ContainerUtil.find(data, s -> StringUtil.startsWith(s, PLUGIN_ID_KEY_PREFIX));
    return pluginIdEntry == null ? null : StringUtil.trimStart(pluginIdEntry, PLUGIN_ID_KEY_PREFIX);
  }

  public static Collection<VirtualFile> findDependsTo(Project project, VirtualFile file) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, getDependsIndexingKey(file.getName()),
                                                           GlobalSearchScopesCore.projectProductionScope(project));
  }

  private static String getDependsIndexingKey(@NotNull String filename) {
    return FILENAME_KEY_PREFIX + filename;
  }
}
