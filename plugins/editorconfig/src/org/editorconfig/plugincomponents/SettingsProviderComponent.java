package org.editorconfig.plugincomponents;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.CachedValueImpl;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.core.EditorConfigException;

import java.util.*;

public class SettingsProviderComponent extends SimpleModificationTracker {
  private static final Key<CachedValue<List<OutPair>>> CACHED_PAIRS = Key.create("editorconfig.cached.pairs");
  private EditorConfig editorConfig;

  public SettingsProviderComponent() {
    editorConfig = new EditorConfig();
  }

  public static SettingsProviderComponent getInstance() {
    return ServiceManager.getService(SettingsProviderComponent.class);
  }

  public List<OutPair> getOutPairs(Project project, VirtualFile file) {
    CachedValue<List<OutPair>> cache = file.getUserData(CACHED_PAIRS);
    if (cache == null) {
      cache = new CachedValueImpl<>(() -> {
        final String filePath = Utils.getFilePath(project, file);
        if (filePath == null) return CachedValueProvider.Result.create(Collections.emptyList(), this);

        final List<OutPair> outPairs;
        try {
          final Set<String> rootDirs = getRootDirs(project);
          outPairs = editorConfig.getProperties(filePath, rootDirs);
          return CachedValueProvider.Result.create(outPairs, this);
        }
        catch (EditorConfigException error) {
          Utils.invalidConfigMessage(project, error.getMessage(), "", filePath);
          return CachedValueProvider.Result.create(Collections.emptyList(), this);
        }
      });
      file.putUserData(CACHED_PAIRS, cache);
    }
    return cache.getValue();
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
