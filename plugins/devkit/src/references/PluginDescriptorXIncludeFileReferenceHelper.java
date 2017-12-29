// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Provides resource roots to be used as contexts in FileReferenceSet during the XIncludes resolving.
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
   return getRoots(project);
  }

  @NotNull
  @Override
  public Collection<PsiFileSystemItem> getRoots(@NotNull Module module) {
    return getRoots(module.getProject());
  }

  private static Collection<PsiFileSystemItem> getRoots(@NotNull Project project) {
    List<VirtualFile> roots = ProjectRootManager.getInstance(project)
      .getModuleSourceRoots(JavaModuleSourceRootTypes.PRODUCTION);
    PsiManager psiManager = PsiManager.getInstance(project);
    return roots.stream()
      .map(virtualFile -> psiManager.findDirectory(virtualFile))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
