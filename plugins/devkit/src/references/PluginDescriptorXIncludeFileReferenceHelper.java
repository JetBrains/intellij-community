// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Provides resource roots to be used as contexts in FileReferenceSet during the XIncludes resolving.
 * @see PluginDescriptorXIncludeReferenceContributor
 */
public class PluginDescriptorXIncludeFileReferenceHelper extends FileReferenceHelper {
  @NotNull
  @Override
  public Collection<PsiFileSystemItem> getContexts(Project project, @NotNull VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) {
      return Collections.emptyList();
    }

    List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE);
    if (resourceRoots.isEmpty()) {
      return Collections.emptyList();
    }

    PsiManager psiManager = PsiManager.getInstance(project);
    return resourceRoots.stream()
      .map(virtualFile -> psiManager.findFile(virtualFile))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Override
  public boolean isMine(Project project, @NotNull VirtualFile file) {
    return file instanceof XmlFile &&
           file.getFileType() == XmlFileType.INSTANCE &&
           PsiUtil.isPluginProject(project) &&
           DescriptorUtil.isPluginXml((XmlFile)file);
  }
}
