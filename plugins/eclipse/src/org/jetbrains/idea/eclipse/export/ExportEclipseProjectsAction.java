// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.export;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl;
import org.jetbrains.idea.eclipse.conversion.DotProjectFileHelper;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathWriter;
import org.jetbrains.idea.eclipse.conversion.EclipseUserLibrariesHelper;
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExportEclipseProjectsAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ExportEclipseProjectsAction.class);

  @Override
  public void update(final @NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    // to flush iml files
    if (project == null) {
      return;
    }

    project.save();

    List<Module> modules = new SmartList<>();
    List<Module> incompatibleModules = new SmartList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!EclipseModuleManagerImpl.isEclipseStorage(module)) {
        try {
          ClasspathStorageProvider provider = ClasspathStorage.getProvider(JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID);
          if (provider != null) {
            provider.assertCompatible(ModuleRootManager.getInstance(module));
          }
          modules.add(module);
        }
        catch (ConfigurationException ignored) {
          incompatibleModules.add(module);
        }
      }
    }

    //todo suggest smth with hierarchy modules
    if (incompatibleModules.isEmpty()) {
      if (modules.isEmpty()) {
        Messages.showInfoMessage(project, EclipseBundle.message("eclipse.export.nothing.to.do"),
                                 EclipseBundle.message("eclipse.export.dialog.title"));
        return;
      }
    }
    else if (Messages.showOkCancelDialog(project, EclipseBundle
                                           .message("dialog.message.incompatible.modules.found", StringUtil.join(incompatibleModules, module -> module.getName(), "<br><li>")),
                                         EclipseBundle.message("eclipse.export.dialog.title"), Messages.getWarningIcon()) != Messages.OK) {
      return;
    }

    modules.addAll(incompatibleModules);
    ExportEclipseProjectsDialog dialog = new ExportEclipseProjectsDialog(project, modules);
    if (!dialog.showAndGet()) {
      return;
    }

    if (dialog.isLink()) {
      for (Module module : dialog.getSelectedModules()) {
        ClasspathStorage.setStorageType(ModuleRootManager.getInstance(module), JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID);
      }
    }
    else {
      Map<Module, String> module2StorageRoot = new LinkedHashMap<>();
      for (Module module : dialog.getSelectedModules()) {
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        String storageRoot = contentRoots.length == 1 ? contentRoots[0].getPath() : ClasspathStorage.getStorageRootFromOptions(module);
        module2StorageRoot.put(module, storageRoot);
        try {
          DotProjectFileHelper.saveDotProjectFile(module, storageRoot);
        }
        catch (Exception e1) {
          LOG.error(e1);
        }
      }

      for (Module module : module2StorageRoot.keySet()) {
        ModuleRootModel model = ModuleRootManager.getInstance(module);
        String storageRoot = module2StorageRoot.get(module);
        try {
          Element classpathElement = new EclipseClasspathWriter().writeClasspath(model);
          File classpathFile = new File(storageRoot, EclipseXml.CLASSPATH_FILE);
          if (!FileUtil.createIfDoesntExist(classpathFile)) {
            continue;
          }
          EclipseJDOMUtil.output(classpathElement, classpathFile, project);

          final Element ideaSpecific = new Element(IdeaXml.COMPONENT_TAG);
          if (IdeaSpecificSettings.writeIdeaSpecificClasspath(ideaSpecific, model)) {
            File emlFile = new File(storageRoot, module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX);
            if (!FileUtil.createIfDoesntExist(emlFile)) {
              continue;
            }
            EclipseJDOMUtil.output(ideaSpecific, emlFile, project);
          }

        }
        catch (Exception e1) {
          LOG.error(e1);
        }
      }
    }
    try {
      EclipseUserLibrariesHelper.appendProjectLibraries(project, dialog.getUserLibrariesFile());
    }
    catch (IOException e1) {
      LOG.error(e1);
    }
    project.save();
  }
}
