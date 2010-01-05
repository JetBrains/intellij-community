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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public class IdeaPluginConverter extends ResolvingConverter<IdeaPlugin> {
  @NonNls private static final Set<String> PLATFORM_MODULES = CollectionFactory.newTroveSet("com.intellij.modules.platform",
                                                                                            "com.intellij.modules.lang",
                                                                                            "com.intellij.modules.vcs",
                                                                                            "com.intellij.modules.xdebugger",
                                                                                            "com.intellij.modules.xml");

  @NotNull
  public Collection<? extends IdeaPlugin> getVariants(final ConvertContext context) {
    return collectAllVisiblePlugins(context.getFile());
  }

  @NotNull
  @Override
  public Set<String> getAdditionalVariants(@NotNull final ConvertContext context) {
    return PLATFORM_MODULES;
  }

  @Override
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return DevKitBundle.message("error.cannot.resolve.plugin", s);
  }

  public static Collection<IdeaPlugin> collectAllVisiblePlugins(@NotNull XmlFile xmlFile) {

    Project project = xmlFile.getProject();
    Module module = ModuleUtil.findModuleForPsiElement(xmlFile);

    GlobalSearchScope scope = module == null ? GlobalSearchScope.allScope(project) :
                              GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true);
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
    }
    return null;
  }

  public String toString(@Nullable final IdeaPlugin ideaPlugin, final ConvertContext context) {
    return ideaPlugin != null ? ideaPlugin.getPluginId() : null;
  }
}
