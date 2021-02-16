// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ContentDescriptor;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.*;

public class ModuleDescriptorNameConverter extends ResolvingConverter<IdeaPlugin> {

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.module.descriptor.name",
                                getDescriptorFilePath(s), s);
  }

  @Override
  public @Nullable IdeaPlugin fromString(@Nullable String s,
                                         ConvertContext context) {
    if (StringUtil.isEmpty(s)) return null;
    final Module currentModule = context.getModule();
    if (currentModule == null) return null;

    final Module module = ModuleManager.getInstance(context.getProject()).findModuleByName(s);
    return module != null && getDependencies(currentModule).contains(module) ? findForModule(module) : null;
  }

  @Override
  public @Nullable String toString(@Nullable IdeaPlugin plugin, ConvertContext context) {
    if (plugin == null) return null;
    return getDisplayName(plugin);
  }

  @NotNull
  private static String getDisplayName(@NotNull IdeaPlugin plugin) {
    return DomUtil.getFile(plugin).getVirtualFile().getNameWithoutExtension();
  }

  @Override
  public @Nullable LookupElement createLookupElement(IdeaPlugin plugin) {
    return LookupElementBuilder.create(plugin, getDisplayName(plugin))
      .withIcon(ElementPresentationManager.getIconForClass(ContentDescriptor.ModuleDescriptor.class))
      .withTypeText(plugin.getPackage().getStringValue());
  }

  @Override
  public @NotNull Collection<? extends IdeaPlugin> getVariants(ConvertContext context) {
    final Module currentModule = context.getModule();
    if (currentModule == null) return Collections.emptyList();

    final Set<Module> dependencies = getDependencies(currentModule);
    List<IdeaPlugin> variants = new SmartList<>();
    for (Module module : dependencies) {
      ContainerUtil.addIfNotNull(variants, findForModule(module));
    }
    return variants;
  }

  @NotNull
  private static Set<Module> getDependencies(Module currentModule) {
    final Set<Module> dependencies = new LinkedHashSet<>();
    ModuleUtilCore.getDependencies(currentModule, dependencies);
    dependencies.remove(currentModule);
    return dependencies;
  }

  @Nullable
  private static IdeaPlugin findForModule(Module module) {
    String moduleName = module.getName();
    final List<VirtualFile> resourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.PRODUCTION);
    for (VirtualFile root : resourceRoots) {
      final VirtualFile candidate = root.findChild(getDescriptorFilePath(moduleName));
      if (candidate == null) continue;
      final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(candidate);
      if (DescriptorUtil.isPluginXml(psiFile)) {
        return DescriptorUtil.getIdeaPlugin((XmlFile)psiFile);
      }
    }
    return null;
  }

  @NotNull
  private static String getDescriptorFilePath(String moduleName) {
    return moduleName + ".xml";
  }
}
