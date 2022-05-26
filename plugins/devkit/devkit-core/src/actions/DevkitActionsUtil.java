// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateFileAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.io.File;
import java.util.*;

public final class DevkitActionsUtil {
  private static final Logger LOG = Logger.getInstance(DevkitActionsUtil.class);

  private DevkitActionsUtil() {
  }


  /**
   * Searches plugin descriptors that belong to modules having dependencies on the specified directory.<br>
   * If the directory belongs to a plugin module, its plugin descriptor is returned immediately.<br>
   * Otherwise dependencies on this directory are analysed. In case of multiple plugin descriptors found, a dialog is shown to
   * select the interesting ones.
   *
   * @param directory directory to analyse dependencies on.
   * @return null if the selection dialog has been cancelled, selected plugin descriptor otherwise.
   */
  @Nullable
  public static XmlFile choosePluginModuleDescriptor(@NotNull PsiDirectory directory) {
    Project project = directory.getProject();
    Module module = getModule(directory);
    if (module != null) {
      List<XmlFile> xmlFiles = choosePluginModuleDescriptors(module);
      if (xmlFiles == null) {
        return null;
      }
      if (!xmlFiles.isEmpty()) {
        assert xmlFiles.size() == 1;
        return xmlFiles.get(0);
      }
    }
    Messages.showMessageDialog(project, DevKitBundle.message("error.no.plugin.xml"), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
    return null;
  }

  @Nullable
  private static List<XmlFile> choosePluginModuleDescriptors(@NotNull Module module) {
    List<Module> pluginModules = getCandidatePluginModules(module);
    if (pluginModules.isEmpty()) {
      return Collections.emptyList();
    }
    if (pluginModules.size() == 1) {
      XmlFile pluginXml = PluginModuleType.getPluginXml(pluginModules.get(0));
      if (pluginXml != null) {
        return Collections.singletonList(pluginXml);
      }
      return Collections.emptyList();
    }

    List<Module> selectedModules = showPluginModuleSelectionDialog(module.getProject(), pluginModules);
    if (selectedModules != null) {
      return ContainerUtil.mapNotNull(selectedModules, m -> PluginModuleType.getPluginXml(m));
    }
    return null;
  }

  @Nullable
  private static List<Module> showPluginModuleSelectionDialog(@NotNull Project project, @NotNull List<Module> pluginModules) {
    String message = DevKitBundle.message("select.plugin.module.to.patch");
    ChoosePluginModuleDialog chooseModulesDialog = new ChoosePluginModuleDialog(project, pluginModules, message, null);
    chooseModulesDialog.setSingleSelectionMode();
    chooseModulesDialog.show();

    List<Module> selectedModules = chooseModulesDialog.getChosenElements();
    if (selectedModules.isEmpty()) {
      return null; // Dialog has been cancelled
    }

    return selectedModules;
  }


  /**
   * Returns all modules that depend on the current one and have plugin descriptors.<br>
   * If the module itself is a plugin module, it is returned immediately.
   */
  @NotNull
  public static List<Module> getCandidatePluginModules(@NotNull Module module) {
    XmlFile currentModulePluginXml = PluginModuleType.getPluginXml(module);
    if (currentModulePluginXml != null) {
      return Collections.singletonList(module);
    }

    List<Module> candidateModules = PluginModuleType.getCandidateModules(module);
    Iterator<Module> it = candidateModules.iterator();
    while (it.hasNext()) {
      Module m = it.next();
      if (PluginModuleType.getPluginXml(m) == null) {
        it.remove();
      }
    }

    return candidateModules;
  }

  /**
   * @throws IncorrectOperationException
   */
  public static void checkCanCreateClass(@NotNull PsiDirectory directory, String name) {
    PsiDirectory currentDir = directory;
    String packageName = StringUtil.getPackageName(name);
    if (!packageName.isEmpty()) {
      for (String dir : packageName.split("\\.")) {
        PsiDirectory childDir = currentDir.findSubdirectory(dir);
        if (childDir == null) {
          return;
        }
        currentDir = childDir;
      }
    }
    JavaDirectoryService.getInstance().checkCreateClass(currentDir, StringUtil.getShortName(name));
  }

  public static PsiClass createSingleClass(String name, String classTemplateName, PsiDirectory directory) {
    return createSingleClass(name, classTemplateName, directory, Collections.emptyMap());
  }

  public static PsiClass createSingleClass(String name, String classTemplateName, PsiDirectory directory,
                                           @NotNull Map<String, String> properties) {
    if (name.contains(".")) {
      String[] names = name.split("\\.");
      for (int i = 0; i < names.length - 1; i++) {
        directory = CreateFileAction.findOrCreateSubdirectory(directory, names[i]);
      }
      name = names[names.length - 1];
    }

    return JavaDirectoryService.getInstance().createClass(directory, name, classTemplateName, false, properties);
  }

  @Nullable
  private static Module getModule(PsiDirectory dir) {
    Project project = dir.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    VirtualFile vFile = dir.getVirtualFile();
    if (fileIndex.isInLibrary(vFile)) {
      List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.isEmpty()) {
        return null;
      }
      Set<Module> modules = new HashSet<>();
      for (OrderEntry orderEntry : orderEntries) {
        modules.add(orderEntry.getOwnerModule());
      }
      Module[] candidates = modules.toArray(Module.EMPTY_ARRAY);
      Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
      return candidates[0];
    }
    return fileIndex.getModuleForFile(vFile);
  }


  private static class ChoosePluginModuleDialog extends ChooseModulesDialog {
    ChoosePluginModuleDialog(Project project, List<? extends Module> items,
                             @NlsContexts.DialogTitle String title,
                             @Nullable @NlsContexts.Label String description) {
      super(project, items, title, description);
    }

    @Override
    protected @Nls String getItemLocation(Module item) {
      XmlFile pluginXml = PluginModuleType.getPluginXml(item);
      if (pluginXml == null) {
        return null;
      }

      VirtualFile virtualFile = pluginXml.getVirtualFile();
      VirtualFile projectPath = item.getProject().getBaseDir();

      if (virtualFile == null) {
        LOG.warn("Unexpected null plugin.xml VirtualFile for module: " + item);
      }
      if (projectPath == null) {
        LOG.warn("Unexpected null project basedir VirtualFile for module: " + item);
      }
      if (virtualFile == null || projectPath == null) {
        return null;
      }

      if (VfsUtilCore.isAncestor(projectPath, virtualFile, false)) {
        return VfsUtilCore.getRelativePath(virtualFile, projectPath, File.separatorChar);
      }
      return virtualFile.getPresentableUrl();
    }
  }
}
