// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.index;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.*;

public class PluginIdDependenciesIndex extends ScalarIndexExtension<String> {

  private static final ID<String, Void> NAME = ID.create("PluginIdDependenciesIndex");

  @NonNls
  private static final String FILENAME_KEY_PREFIX = "___FILENAME___";

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
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return inputData -> {
      IdeaPlugin plugin = RegistrationIndexer.obtainIdeaPlugin(inputData);
      if (plugin == null) return Collections.emptyMap();

      List<String> ids = new SmartList<>();
      ContainerUtil.addIfNotNull(ids, plugin.getPluginId());
      for (Dependency dependency : plugin.getDependencies()) {
        ContainerUtil.addIfNotNull(ids, dependency.getStringValue());

        final String configFile = dependency.getConfigFile().getStringValue();
        if (configFile != null) {
          final String filename = PathUtil.getFileName(configFile);
          ids.add(getDependsIndexingKey(filename));
        }
      }

      return ContainerUtil.newHashMap(ids, Collections.nCopies(ids.size(), null));
    };
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  public static Set<String> getPluginAndDependsIds(Project project, Set<VirtualFile> files) {
    Set<String> ids = new SmartHashSet<>();
    for (VirtualFile file : files) {
      final Map<String, Void> data = FileBasedIndex.getInstance().getFileData(NAME, file, project);
      ids.addAll(ContainerUtil.filter(data.keySet(), s -> !StringUtil.startsWith(s, FILENAME_KEY_PREFIX)));
    }
    return ids;
  }

  public static Collection<VirtualFile> findDependsTo(Project project, VirtualFile file) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, getDependsIndexingKey(file.getName()),
                                                           GlobalSearchScopesCore.projectProductionScope(project));
  }

  private static String getDependsIndexingKey(String filename) {
    return FILENAME_KEY_PREFIX + filename;
  }
}
