// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.ide;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.*;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

final class PluginDescriptorDomFileSearchScopeProvider implements SearchScopeProvider {

  @Override
  public @Nullable String getDisplayName() {
    return DevKitBundle.message("plugin.xml.scopes.display.name");
  }

  @Override
  public @NotNull List<SearchScope> getSearchScopes(@NotNull Project project, @NotNull DataContext dataContext) {
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) return Collections.emptyList();

    GlobalSearchScope scope = GlobalSearchScope.filesScope(project, () ->
      DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, GlobalSearchScopesCore.projectProductionScope(project))
    );
    return Collections.singletonList(new DelegatingGlobalSearchScope(scope) {
      @Override
      public @NotNull String getDisplayName() {
        return DevKitBundle.message("plugin.xml.scopes.production.display.name");
      }

      @Override
      public Icon getIcon() {
        return AllIcons.Nodes.Plugin;
      }
    });
  }
}
