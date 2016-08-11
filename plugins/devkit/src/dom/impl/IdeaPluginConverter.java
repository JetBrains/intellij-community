/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.ResolvingConverter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.PluginModule;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public class IdeaPluginConverter extends ResolvingConverter<IdeaPlugin> {

  private static final Condition<IdeaPlugin> NON_CORE_PLUGINS = plugin -> !"com.intellij".equals(plugin.getPluginId());

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
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return DevKitBundle.message("error.cannot.resolve.plugin", s);
  }

  public IdeaPlugin fromString(@Nullable @NonNls final String s, final ConvertContext context) {
    for (IdeaPlugin ideaPlugin : getAllPluginsWithoutSelf(context)) {
      final String otherId = ideaPlugin.getPluginId();
      if (otherId == null) continue;
      if (otherId.equals(s)) return ideaPlugin;
      for (PluginModule module : ideaPlugin.getModules()) {
        final String moduleName = module.getValue().getValue();
        if (moduleName != null && moduleName.equals(s)) return ideaPlugin;
      }
    }
    return null;
  }

  public String toString(@Nullable final IdeaPlugin ideaPlugin, final ConvertContext context) {
    return ideaPlugin != null ? ideaPlugin.getPluginId() : null;
  }

  private static Collection<IdeaPlugin> getAllPluginsWithoutSelf(final ConvertContext context) {
    final IdeaPlugin self = context.getInvocationElement().getParentOfType(IdeaPlugin.class, true);
    if (self == null) return Collections.emptyList();

    final Collection<IdeaPlugin> plugins = getAllPlugins(context.getProject());
    return ContainerUtil.filter(plugins, plugin -> !Comparing.strEqual(self.getPluginId(), plugin.getPluginId()));
  }

  public static Collection<IdeaPlugin> getAllPlugins(final Project project) {
    if (DumbService.isDumb(project)) return Collections.emptyList();
    
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project).
        union(ProjectScope.getLibrariesScope(project));
      return CachedValueProvider.Result.create(getPlugins(project, scope), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    });
  }

  @NotNull
  public static Collection<IdeaPlugin> getPlugins(Project project, GlobalSearchScope scope) {
    if (DumbService.isDumb(project)) return Collections.emptyList();

    List<DomFileElement<IdeaPlugin>> files = DomService.getInstance().getFileElements(IdeaPlugin.class, project, scope);
    return ContainerUtil.map(files, ideaPluginDomFileElement -> ideaPluginDomFileElement.getRootElement());
  }
}
