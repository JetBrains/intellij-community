package org.editorconfig.plugincomponents;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.core.EditorConfigException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SettingsProviderComponent {
  private EditorConfig editorConfig;

  public SettingsProviderComponent() {
    editorConfig = new EditorConfig();
  }

  public static SettingsProviderComponent getInstance() {
    return ServiceManager.getService(SettingsProviderComponent.class);
  }

  public List<OutPair> getOutPairs(Project project, String filePath) {
    if (filePath == null) return Collections.emptyList();

    final List<OutPair> outPairs;
    try {
      final Set<String> rootDirs = getRootDirs(project);
      outPairs = editorConfig.getProperties(filePath, rootDirs);
      return outPairs;
    }
    catch (EditorConfigException error) {
      Utils.invalidConfigMessage(project, error.getMessage(), "", filePath);
      return new ArrayList<>();
    }
  }

  public Set<String> getRootDirs(final Project project) {
    if (!Registry.is("editor.config.stop.at.project.root")) {
      return Collections.emptySet();
    }

    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      final Set<String> dirs = new HashSet<>();
      final VirtualFile projectBase = project.getBaseDir();
      if (projectBase != null) {
        dirs.add(project.getBasePath());
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
            if (!VfsUtilCore.isAncestor(projectBase, root, false)) {
              dirs.add(root.getPath());
            }
          }
        }
      }
      dirs.add(PathManager.getConfigPath());
      return new CachedValueProvider.Result<>(dirs, ProjectRootModificationTracker.getInstance(project));
    });
  }
}
