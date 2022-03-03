// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ContentDescriptor;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.*;

public class ModuleDescriptorNameConverter extends ResolvingConverter<IdeaPlugin> {

  @NonNls
  private static final String SUB_DESCRIPTOR_DELIMITER = "/";
  @NonNls
  private static final String SUB_DESCRIPTOR_FILENAME_DELIMITER = ".";

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    String value = StringUtil.notNullize(s);

    String filePath;
    String moduleName;
    if (isSubDescriptor(value)) {
      filePath = getSubDescriptorFilePath(value);
      moduleName = getSubDescriptorModuleName(value);
    }
    else {
      filePath = getDescriptorFilePath(value);
      moduleName = value;
    }

    return DevKitBundle.message("plugin.xml.convert.module.descriptor.name",
                                filePath, moduleName);
  }

  @Override
  public @Nullable IdeaPlugin fromString(@Nullable String s,
                                         ConvertContext context) {
    if (StringUtil.isEmpty(s)) return null;
    final Module currentModule = context.getModule();
    if (currentModule == null) return null;
    final ModuleManager moduleManager = ModuleManager.getInstance(context.getProject());

    if (isSubDescriptor(s)) {
      final Module module = moduleManager.findModuleByName(getSubDescriptorModuleName(s));
      if (module == null) return null;
      return findDescriptorFile(module, getSubDescriptorFilePath(s));
    }

    final Module module = moduleManager.findModuleByName(s);
    if (module == null) return null;

    return findDescriptorFile(module, getDescriptorFilePath(module.getName()));
  }

  @Override
  public @Nullable String toString(@Nullable IdeaPlugin plugin, ConvertContext context) {
    if (plugin == null) return null;
    return getDisplayName(plugin);
  }

  @NotNull
  private static String getDisplayName(@NotNull IdeaPlugin plugin) {
    final Module module = Objects.requireNonNull(plugin.getModule());
    final String moduleName = module.getName();

    final VirtualFile virtualFile = DomUtil.getFile(plugin).getVirtualFile();
    final String fileName = virtualFile.getNameWithoutExtension();
    if (moduleName.equals(fileName)) {
      return fileName;
    }
    return moduleName + SUB_DESCRIPTOR_DELIMITER + StringUtil.substringAfterLast(fileName, SUB_DESCRIPTOR_FILENAME_DELIMITER);
  }

  @Override
  public @NotNull LookupElement createLookupElement(IdeaPlugin plugin) {
    final String displayName = getDisplayName(plugin);
    LookupElementBuilder builder = LookupElementBuilder.create(Objects.requireNonNull(getPsiElement(plugin)), displayName)
      .withIcon(ElementPresentationManager.getIconForClass(ContentDescriptor.ModuleDescriptor.class))
      .withBoldness(isSubDescriptor(displayName))
      .withTypeText(plugin.getPackage().getStringValue());

    Double priority = plugin.getUserData(LOOKUP_PRIORITY);
    return priority != null ? PrioritizedLookupElement.withPriority(builder, priority) : builder;
  }

  private static final Key<Double> LOOKUP_PRIORITY = Key.create("LOOKUP_PRIORITY");

  @Override
  public @NotNull Collection<? extends IdeaPlugin> getVariants(ConvertContext context) {
    final Module currentModule = context.getModule();
    if (currentModule == null) return Collections.emptyList();
    final Project project = context.getProject();
    List<IdeaPlugin> variants = new SmartList<>();

    final Set<Module> dependencies = new LinkedHashSet<>();
    ModuleUtilCore.getDependencies(currentModule, dependencies);
    dependencies.remove(currentModule);

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      boolean prioritize = module == currentModule || dependencies.contains(module);
      String moduleName = module.getName();

      processModuleSourceRoots(module, root -> {
        final Collection<IdeaPlugin> plugins = DescriptorUtil.getPlugins(project, GlobalSearchScopes.directoryScope(project, root, false));
        if (prioritize) {
          plugins.forEach(plugin -> plugin.putUserData(LOOKUP_PRIORITY, module == currentModule ? 200.0 : 100.0));
        }
        variants.addAll(ContainerUtil.filter(plugins, plugin -> DomUtil.getFile(plugin).getName().startsWith(moduleName)));
        return true;
      });
    }
    return variants;
  }

  @Nullable
  private static IdeaPlugin findDescriptorFile(@NotNull Module module, @NotNull String filePath) {
    Ref<IdeaPlugin> ideaPlugin = Ref.create();
    processModuleSourceRoots(module, root -> {
      final VirtualFile candidate = root.findChild(filePath);
      if (candidate == null) return true;
      final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(candidate);
      if (DescriptorUtil.isPluginXml(psiFile)) {
        ideaPlugin.set(DescriptorUtil.getIdeaPlugin((XmlFile)psiFile));
        return false;
      }
      return true;
    });
    return ideaPlugin.get();
  }

  private static void processModuleSourceRoots(@NotNull Module module, Processor<VirtualFile> processor) {
    for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE)) {
      if (!processor.process(root)) return;
    }
    for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE)) {
      if (!processor.process(root)) return;
    }
  }

  @NotNull
  private static String getDescriptorFilePath(@NotNull String fileName) {
    return fileName + ".xml";
  }

  private static boolean isSubDescriptor(@NotNull String value) {
    return StringUtil.contains(value, SUB_DESCRIPTOR_DELIMITER);
  }

  @NotNull
  private static String getSubDescriptorModuleName(@NotNull String value) {
    final String moduleName = StringUtil.substringBefore(value, SUB_DESCRIPTOR_DELIMITER);
    assert moduleName != null : value;
    return moduleName;
  }

  @NotNull
  private static String getSubDescriptorFilePath(@NotNull String value) {
    final String moduleName = getSubDescriptorModuleName(value);
    final String fileName = StringUtil.substringAfter(value, SUB_DESCRIPTOR_DELIMITER);
    assert fileName != null : value;
    return getDescriptorFilePath(moduleName + SUB_DESCRIPTOR_FILENAME_DELIMITER + fileName);
  }
}
