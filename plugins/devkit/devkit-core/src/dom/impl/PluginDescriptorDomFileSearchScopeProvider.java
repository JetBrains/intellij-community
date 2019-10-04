// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.*;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.PsiUtil;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PluginDescriptorDomFileSearchScopeProvider implements SearchScopeProvider {

  @Nullable
  @Override
  public String getDisplayName() {
    return "Plugin Descriptor Files";
  }

  @NotNull
  @Override
  public List<SearchScope> getSearchScopes(@NotNull Project project) {
    if (DumbService.isDumb(project) || !PsiUtil.isIdeaProject(project)) return Collections.emptyList();

    final Collection<VirtualFile> pluginDescriptorFiles =
      DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, project, GlobalSearchScopesCore.projectProductionScope(project));
    GlobalSearchScope scope = GlobalSearchScope.filesScope(project, pluginDescriptorFiles);
    return Collections.singletonList(new DelegatingGlobalSearchScope(scope) {
      @NotNull
      @Override
      public String getDisplayName() {
        return "All Production " + PluginDescriptorDomFileSearchScopeProvider.this.getDisplayName();
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return AllIcons.Nodes.Plugin;
      }
    });
  }
}
