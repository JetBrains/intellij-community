// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.ide;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.SearchScopeProvider;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import javax.swing.Icon;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

final class PluginDescriptorDomFileSearchScopeProvider implements SearchScopeProvider {

  @Override
  public String getDisplayName() {
    return DevKitBundle.message("plugin.xml.scopes.display.name");
  }

  @Override
  public @NotNull List<SearchScope> getSearchScopes(@NotNull Project project, @NotNull DataContext dataContext) {
    if (!IntelliJProjectUtil.isIntelliJPlatformProject(project)) return Collections.emptyList();
    GlobalSearchScope scope = GlobalSearchScope.filesScope(project, getPluginDescriptorsScopeSupplier(project));
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

  private static @NotNull Supplier<Collection<? extends VirtualFile>> getPluginDescriptorsScopeSupplier(@NotNull Project project) {
    return () -> {
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        return getDomFileCandidates(project);
      }
      return ReadAction.nonBlocking(() -> getDomFileCandidates(project))
        .expireWhen(() -> project.isDisposed())
        .executeSynchronously();
    };
  }

  private static @NotNull Collection<VirtualFile> getDomFileCandidates(@NotNull Project project) {
    if (DumbService.isDumb(project)) return Collections.emptyList();
    return DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, GlobalSearchScopesCore.projectProductionScope(project));
  }
}
