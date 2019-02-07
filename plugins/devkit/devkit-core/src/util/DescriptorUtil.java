// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.ArrayList;
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

  @Nullable
  public static String getPluginId(Module plugin) {
    assert PluginModuleType.isOfType(plugin);

    final XmlFile pluginXml = PluginModuleType.getPluginXml(plugin);
    if (pluginXml == null) {
      return null;
    }
    final DomFileElement<IdeaPlugin> ideaPlugin = getIdeaPlugin(pluginXml);
    if (ideaPlugin == null) {
      return null;
    }

    return ideaPlugin.getRootElement().getPluginId();
  }

  public static List<String> getPluginAndOptionalDependenciesIds(Module module) {
    XmlFile xml = PluginModuleType.getPluginXml(module);
    if (xml == null) return Collections.emptyList();
    DomFileElement<IdeaPlugin> plugin = getIdeaPlugin(xml);
    if (plugin == null) return Collections.emptyList();
    List<String> result = new ArrayList<>();
    ContainerUtil.addIfNotNull(result, plugin.getRootElement().getPluginId());
    for (Dependency dependency : plugin.getRootElement().getDependencies()) {
      if (Boolean.TRUE.equals(dependency.getOptional().getValue())) {
        ContainerUtil.addIfNotNull(result, dependency.getRawText());
      }
    }
    return result;
  }

  public static boolean isPluginXml(@Nullable PsiFile file) {
    if (!(file instanceof XmlFile)) return false;
    return getIdeaPlugin((XmlFile)file) != null;
  }

  @Nullable
  public static DomFileElement<IdeaPlugin> getIdeaPlugin(@NotNull XmlFile file) {
    return DomManager.getDomManager(file.getProject()).getFileElement(file, IdeaPlugin.class);
  }
}
