// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public final class HgExecutableManager {
  public static HgExecutableManager getInstance() {
    return ApplicationManager.getApplication().getService(HgExecutableManager.class);
  }

  private static final String[] DEFAULT_WINDOWS_PATHS = {
    "C:\\Program Files\\Mercurial",
    "C:\\Program Files (x86)\\Mercurial",
    "C:\\cygwin\\bin"
  };
  private static final String[] DEFAULT_UNIX_PATHS = {
    "/usr/local/bin",
    "/usr/bin",
    "/opt/local/bin",
    "/opt/bin",
    "/usr/local/mercurial"
  };

  private final @NotNull NotNullLazyValue<String> myDetectedExecutable;

  public HgExecutableManager() {
    myDetectedExecutable = NotNullLazyValue.atomicLazy(HgExecutableManager::identifyDefaultHgExecutable);
  }

  public @NotNull String getHgExecutable() {
    String path = HgGlobalSettings.getInstance().getHgExecutable();
    return path == null ? getDefaultExecutable() : path;
  }

  public @NotNull String getHgExecutable(@NotNull Project project) {
    HgProjectSettings projectSettings = HgProjectSettings.getInstance(project);
    if (!projectSettings.isHgExecutableOverridden()) return getHgExecutable();

    String path = projectSettings.getHgExecutable();
    return path == null ? getDefaultExecutable() : path;
  }

  public @NotNull String getDefaultExecutable() {
    return myDetectedExecutable.getValue();
  }

  /// @return the default executable name depending on the platform
  private static @NotNull String identifyDefaultHgExecutable() {
    var executableName = OS.CURRENT.getBinaryName("hg");

    var hgExecutableFromPath = PathEnvironmentVariableUtil.findFirst(executableName);
    if (hgExecutableFromPath != null) {
      return hgExecutableFromPath.toString();
    }

    var paths = OS.CURRENT == OS.Windows ? DEFAULT_WINDOWS_PATHS : DEFAULT_UNIX_PATHS;
    for (var path : paths) {
      var executablePath = Path.of(path, executableName);
      if (Files.isExecutable(executablePath)) {
        return executablePath.toString();
      }
    }

    // otherwise, let's hope it's in $PATH
    return executableName;
  }
}
