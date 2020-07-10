// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex;
import org.jetbrains.idea.devkit.dom.index.PluginIdDependenciesIndex;
import org.jetbrains.idea.devkit.dom.index.PluginIdModuleIndex;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;
import java.util.function.Supplier;

public class ExtensionsDomExtender extends DomExtender<Extensions> {

  private static final DomExtender<Extension> EXTENSION_EXTENDER = new ExtensionDomExtender();

  @Override
  public boolean supportsStubs() {
    return false;
  }

  @Override
  public void registerExtensions(@NotNull final Extensions extensions, @NotNull final DomExtensionsRegistrar registrar) {
    Project project = extensions.getManager().getProject();
    VirtualFile currentFile = getVirtualFile(extensions);
    if (currentFile == null || DumbService.isDumb(project)) return;

    Set<VirtualFile> files = getVisibleFiles(project, currentFile);

    String epPrefix = extensions.getEpPrefix();
    Map<String, Supplier<ExtensionPoint>> points = ExtensionPointIndex.getExtensionPoints(project, files, epPrefix);

    for (Map.Entry<String, Supplier<ExtensionPoint>> entry : points.entrySet()) {
      registrar.registerCollectionChildrenExtension(new XmlName(entry.getKey().substring(epPrefix.length())), Extension.class)
        .setDeclaringDomElement(entry.getValue())
        .addExtender(EXTENSION_EXTENDER);
    }
  }

  @Nullable
  private static VirtualFile getVirtualFile(DomElement domElement) {
    final VirtualFile file = DomUtil.getFile(domElement).getOriginalFile().getVirtualFile();
    return file instanceof VirtualFileWithId ? file : null;
  }

  private static Set<VirtualFile> getVisibleFiles(Project project, @NotNull VirtualFile file) {
    Set<VirtualFile> result = new HashSet<>();
    collectFiles(project, file, result);
    result.addAll(PluginIdModuleIndex.getFiles(project, ""));
    return result;
  }

  private static void collectFiles(Project project, @NotNull VirtualFile file, Set<VirtualFile> result) {
    ProgressManager.checkCanceled();
    if (!result.add(file)) {
      return;
    }

    for (String id : getDependencies(project, file)) {
      for (VirtualFile dep : PluginIdModuleIndex.getFiles(project, id)) {
        collectFiles(project, dep, result);
      }
    }
  }

  static Collection<String> getDependencies(IdeaPlugin ideaPlugin) {
    final VirtualFile currentFile = getVirtualFile(ideaPlugin);
    if (currentFile == null) {
      return Collections.emptySet();
    }

    final Project project = ideaPlugin.getManager().getProject();
    return getDependencies(project, currentFile);
  }

  private static Collection<String> getDependencies(Project project, @NotNull VirtualFile currentFile) {
    Set<String> result = new HashSet<>();
    result.add(PluginManagerCore.CORE_PLUGIN_ID);

    result.addAll(PluginIdDependenciesIndex.getPluginAndDependsIds(project, Collections.singleton(currentFile)));

    final String pluginId = PluginIdDependenciesIndex.getPluginId(project, currentFile);
    if (pluginId != null) {
      result.remove(pluginId);
      return result;
    }

    final VirtualFile[] includingFiles = FileIncludeManager.getManager(project).getIncludingFiles(currentFile, false);

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Set<VirtualFile> includingAndDependsFiles = new SmartHashSet<>();
    for (VirtualFile virtualFile : includingFiles) {
      if (!fileIndex.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.PRODUCTION)) {
        continue;
      }
      includingAndDependsFiles.add(virtualFile);
    }

    final Collection<VirtualFile> dependsToFiles = PluginIdDependenciesIndex.findDependsTo(project, currentFile);
    includingAndDependsFiles.addAll(dependsToFiles);

    if (includingAndDependsFiles.isEmpty()) {
      return result;
    }

    final Set<String> ids = PluginIdDependenciesIndex.getPluginAndDependsIds(project, includingAndDependsFiles);
    result.addAll(ids);
    return result;
  }
}
