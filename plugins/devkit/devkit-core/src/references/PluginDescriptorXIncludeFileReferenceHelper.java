// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides resource roots to be used as contexts in FileReferenceSet during the XIncludes resolving.
 *
 * @see PluginDescriptorXIncludeReferenceContributor
 */
public class PluginDescriptorXIncludeFileReferenceHelper extends FileReferenceHelper {
  @Override
  public boolean isMine(Project project, @NotNull VirtualFile file) {
    return file.getFileType() == XmlFileType.INSTANCE &&
           PsiUtil.isPluginProject(project) &&
           DescriptorUtil.isPluginXml(PsiManager.getInstance(project).findFile(file));
  }

  @NotNull
  @Override
  public Collection<PsiFileSystemItem> getContexts(Project project, @NotNull VirtualFile file) {
    return getRootsContainingPluginXmlFiles(project);
  }

  @NotNull
  @Override
  public Collection<PsiFileSystemItem> getRoots(@NotNull Module module) {
    return getRootsContainingPluginXmlFiles(module.getProject());
  }

  private static Collection<PsiFileSystemItem> getRootsContainingPluginXmlFiles(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {

      Collection<VirtualFile> pluginXmlFilesInProductionScope =
        DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, project,
                                                      GlobalSearchScopesCore.projectProductionScope(project));

      ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
      Set<VirtualFile> pluginXmlSourceRoots = new HashSet<>();
      for (VirtualFile pluginXml : pluginXmlFilesInProductionScope) {
        VirtualFile sourceRoot = projectFileIndex.getSourceRootForFile(pluginXml);
        ContainerUtil.addIfNotNull(pluginXmlSourceRoots, sourceRoot);
      }

      PsiManager psiManager = PsiManager.getInstance(project);
      List<PsiFileSystemItem> sourceRootsPsiFileSystemItems =
        pluginXmlSourceRoots.stream()
                            .map(virtualFile -> psiManager.findDirectory(virtualFile))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
      return CachedValueProvider.Result.create(sourceRootsPsiFileSystemItems, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}
