// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.Collection;

public class PluginRelatedLocatorsUtils {
  @NotNull
  public static GlobalSearchScope getCandidatesScope(@NotNull Project project) {
    GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project)
      .uniteWith(LibraryScopeCache.getInstance(project).getLibrariesOnlyScope());

    Collection<VirtualFile> candidates = DomService.getInstance()
      .getDomFileCandidates(IdeaPlugin.class, project, scope);
    return GlobalSearchScope.filesWithLibrariesScope(project, candidates);
  }
}
