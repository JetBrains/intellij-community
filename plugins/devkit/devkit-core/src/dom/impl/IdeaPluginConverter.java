// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.PluginModule;
import org.jetbrains.idea.devkit.dom.index.PluginIdModuleIndex;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class IdeaPluginConverter extends IdeaPluginConverterBase {

  private static final Condition<IdeaPlugin> NON_CORE_PLUGINS = plugin -> plugin.hasRealPluginId();

  @Override
  @NotNull
  public Collection<? extends IdeaPlugin> getVariants(final ConvertContext context) {
    Collection<IdeaPlugin> plugins = getAllPluginsWithoutSelf(context);
    return ContainerUtil.filter(plugins, NON_CORE_PLUGINS);
  }

  @NotNull
  @Override
  public Set<String> getAdditionalVariants(@NotNull final ConvertContext context) {
    final THashSet<String> result = new THashSet<>();
    for (IdeaPlugin ideaPlugin : getAllPluginsWithoutSelf(context)) {
      for (PluginModule module : ideaPlugin.getModules()) {
        ContainerUtil.addIfNotNull(result, module.getValue().getValue());
      }
    }
    return result;
  }

  @Override
  public IdeaPlugin fromString(@Nullable @NonNls final String s, final ConvertContext context) {
    return s == null ? null : ContainerUtil.getFirstItem(PluginIdModuleIndex.findPlugins(context.getInvocationElement(), s));
  }

  private static Collection<IdeaPlugin> getAllPluginsWithoutSelf(final ConvertContext context) {
    final IdeaPlugin self = context.getInvocationElement().getParentOfType(IdeaPlugin.class, true);
    if (self == null) return Collections.emptyList();

    final Collection<IdeaPlugin> plugins = getAllPlugins(context.getProject());
    return ContainerUtil.filter(plugins, plugin -> !Comparing.strEqual(self.getPluginId(), plugin.getPluginId()));
  }

  private static Collection<IdeaPlugin> getAllPlugins(final Project project) {
    if (DumbService.isDumb(project)) return Collections.emptyList();

    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project).
        union(ProjectScope.getLibrariesScope(project));
      return CachedValueProvider.Result.create(DescriptorUtil.getPlugins(project, scope), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}
