/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
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
import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public class IdeaPluginConverter extends ResolvingConverter<IdeaPlugin> {

  @NotNull
  public Collection<? extends IdeaPlugin> getVariants(final ConvertContext context) {
    return collectAllVisiblePlugins(context.getFile());
  }

  @NotNull
  @Override
  public Set<String> getAdditionalVariants(@NotNull final ConvertContext context) {
    final THashSet<String> result = new THashSet<String>();
    for (IdeaPlugin ideaPlugin : getVariants(context)) {
      for (PluginModule module : ideaPlugin.getModules()) {
        ContainerUtil.addIfNotNull(module.getValue().getValue(), result);
      }
    }
    return result;
  }

  @Override
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return DevKitBundle.message("error.cannot.resolve.plugin", s);
  }

  public static Collection<IdeaPlugin> collectAllVisiblePlugins(@NotNull XmlFile xmlFile) {

    Project project = xmlFile.getProject();
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    List<DomFileElement<IdeaPlugin>> files = DomService.getInstance().getFileElements(IdeaPlugin.class, project, scope);
    return ContainerUtil.map(files, new Function<DomFileElement<IdeaPlugin>, IdeaPlugin>() {
      public IdeaPlugin fun(DomFileElement<IdeaPlugin> ideaPluginDomFileElement) {
        return ideaPluginDomFileElement.getRootElement();
      }
    });
  }

  public IdeaPlugin fromString(@Nullable @NonNls final String s, final ConvertContext context) {
    for (IdeaPlugin ideaPlugin : getVariants(context)) {
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
}
