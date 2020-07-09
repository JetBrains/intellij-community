// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.plugincomponents;

import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.CachedValueImpl;
import org.editorconfig.EditorConfigRegistry;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.core.EditorConfigException;
import org.editorconfig.core.ParserCallback;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SettingsProviderComponent extends SimpleModificationTracker {
  private static final Key<CachedValue<List<OutPair>>> CACHED_PAIRS = Key.create("editorconfig.cached.pairs");
  public static final String ERROR = "___error___";
  private final EditorConfig editorConfig;

  public SettingsProviderComponent() {
    editorConfig = new EditorConfig();
  }

  public static SettingsProviderComponent getInstance() {
    return ApplicationManager.getApplication().getService(SettingsProviderComponent.class);
  }

  public List<OutPair> getOutPairs(Project project, VirtualFile file) {
    return getOutPairs(project, file, null);
  }

  public List<OutPair> getOutPairs(Project project, VirtualFile file, @Nullable ParserCallback callback) {
    final String filePath = Utils.getFilePath(project, file);
    if (filePath == null) return Collections.emptyList();
    final UserDataHolder dataHolder = CodeStyleCachingService.getInstance(project).getDataHolder(file);
    if (dataHolder == null) return Collections.emptyList();
    CachedValue<List<OutPair>> cache = dataHolder.getUserData(CACHED_PAIRS);
    if (cache == null) {
      final Set<String> rootDirs = getRootDirs(project);
      cache = new CachedValueImpl<>(() -> {
        final List<OutPair> outPairs;
        try {
          outPairs = editorConfig.getProperties(filePath, rootDirs, callback);
          return CachedValueProvider.Result.create(outPairs, this);
        }
        catch (EditorConfigException error) {
          ArrayList<OutPair> errorResult = new ArrayList<>();
          errorResult.add(new OutPair(ERROR, error.getMessage()));
          return CachedValueProvider.Result.create(errorResult, this);
        }
      });
      dataHolder.putUserData(CACHED_PAIRS, cache);
    }
    return cache.getValue();
  }

  public Set<String> getRootDirs(final Project project) {
    if (!EditorConfigRegistry.shouldStopAtProjectRoot()) {
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
