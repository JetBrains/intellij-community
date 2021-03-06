// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class DescriptorUtil {
  private DescriptorUtil() {
  }

  public interface Patcher {
    void patchPluginXml(XmlFile pluginXml, PsiClass klass) throws IncorrectOperationException;
  }

  public static void processComponents(XmlTag root, ComponentType.Processor processor) {
    final ComponentType[] types = ComponentType.values();
    for (ComponentType type : types) {
      type.process(root, processor);
    }
  }

  public static void processActions(XmlTag root, ActionType.Processor processor) {
    final ActionType[] types = ActionType.values();
    for (ActionType type : types) {
      type.process(root, processor);
    }
  }

  public static void patchPluginXml(Patcher patcher, PsiClass klass, XmlFile pluginXml) throws IncorrectOperationException {
    checkPluginXmlsWritable(klass.getProject(), pluginXml);
    WriteAction.run((ThrowableRunnable<IncorrectOperationException>)() -> patcher.patchPluginXml(pluginXml, klass));
  }

  public static void checkPluginXmlsWritable(Project project, XmlFile pluginXml) {
    VirtualFile file = pluginXml.getVirtualFile();

    final ReadonlyStatusHandler readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project);
    final ReadonlyStatusHandler.OperationStatus status = readonlyStatusHandler.ensureFilesWritable(Collections.singletonList(file));
    if (status.hasReadonlyFiles()) {
      throw new IncorrectOperationException(DevKitBundle.message("error.plugin.xml.readonly", status.getReadonlyFiles()[0]));
    }
  }

  public static List<String> getPluginAndOptionalDependenciesIds(Module module) {
    XmlFile xml = PluginModuleType.getPluginXml(module);
    if (xml == null) return Collections.emptyList();
    IdeaPlugin plugin = getIdeaPlugin(xml);
    if (plugin == null) return Collections.emptyList();
    List<String> result = new ArrayList<>();
    ContainerUtil.addIfNotNull(result, plugin.getPluginId());
    for (Dependency dependency : plugin.getDepends()) {
      if (Boolean.TRUE.equals(dependency.getOptional().getValue())) {
        ContainerUtil.addIfNotNull(result, dependency.getRawText());
      }
    }
    return result;
  }

  public static boolean isPluginXml(@Nullable PsiFile file) {
    if (!(file instanceof XmlFile)) return false;
    return getIdeaPluginFileElement((XmlFile)file) != null;
  }

  @Nullable
  public static DomFileElement<IdeaPlugin> getIdeaPluginFileElement(@NotNull XmlFile file) {
    return DomManager.getDomManager(file.getProject()).getFileElement(file, IdeaPlugin.class);
  }

  @Nullable
  public static IdeaPlugin getIdeaPlugin(@NotNull XmlFile file) {
    final DomFileElement<IdeaPlugin> plugin = getIdeaPluginFileElement(file);
    return plugin != null ? plugin.getRootElement() : null;
  }

  @NotNull
  public static Collection<IdeaPlugin> getPlugins(Project project, GlobalSearchScope scope) {
    if (DumbService.isDumb(project)) return Collections.emptyList();

    List<DomFileElement<IdeaPlugin>> files = DomService.getInstance().getFileElements(IdeaPlugin.class, project, scope);
    return ContainerUtil.map(files, ideaPluginDomFileElement -> ideaPluginDomFileElement.getRootElement());
  }

}
