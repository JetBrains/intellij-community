// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.ide;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.*;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.PsiUtil;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

final class PluginDescriptorDomFileSearchScopeProvider implements SearchScopeProvider {

  @Nullable
  @Override
  public String getDisplayName() {
    return DevKitBundle.message("plugin.xml.scopes.display.name");
  }

  @NotNull
  @Override
  public List<SearchScope> getSearchScopes(@NotNull Project project, @NotNull DataContext dataContext) {
    if (!PsiUtil.isIdeaProject(project)) return Collections.emptyList();

    GlobalSearchScope scope = GlobalSearchScope.filesScope(project, () ->
      DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, GlobalSearchScopesCore.projectProductionScope(project))
    );
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
