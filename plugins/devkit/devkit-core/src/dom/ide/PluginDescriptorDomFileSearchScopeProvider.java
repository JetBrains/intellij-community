// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.ide;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.*;
import com.intellij.util.SlowOperations;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
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
    return DevKitBundle.message("plugin.xml.scopes.display.name");
  }

  @NotNull
  @Override
  public List<SearchScope> getSearchScopes(@NotNull Project project, @NotNull DataContext dataContext) {
    if (DumbService.isDumb(project) || !PsiUtil.isIdeaProject(project)) return Collections.emptyList();

    final Collection<VirtualFile> pluginDescriptorFiles = SlowOperations.allowSlowOperations(
      () -> DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, GlobalSearchScopesCore.projectProductionScope(project))
    );
    GlobalSearchScope scope = GlobalSearchScope.filesScope(project, pluginDescriptorFiles);
    return Collections.singletonList(new DelegatingGlobalSearchScope(scope) {
      @NotNull
      @Override
      public String getDisplayName() {
        return DevKitBundle.message("plugin.xml.scopes.production.display.name");
      }

      @Override
      public Icon getIcon() {
        return AllIcons.Nodes.Plugin;
      }
    });
  }
}
