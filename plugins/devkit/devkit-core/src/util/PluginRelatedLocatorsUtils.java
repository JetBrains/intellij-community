// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.Collection;

public final class PluginRelatedLocatorsUtils {
  @NotNull
  public static GlobalSearchScope getCandidatesScope(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project)
        .uniteWith(LibraryScopeCache.getInstance(project).getLibrariesOnlyScope());

      Collection<VirtualFile> candidates = DomService.getInstance()
        .getDomFileCandidates(IdeaPlugin.class, scope);
      final GlobalSearchScope filesScope = GlobalSearchScope.filesWithLibrariesScope(project, candidates);
      return new CachedValueProvider.Result<>(filesScope, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}
