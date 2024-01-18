// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public final class HgExecutableManager {
  public static HgExecutableManager getInstance() {
    return ApplicationManager.getApplication().getService(HgExecutableManager.class);
  }

  private static final @NonNls String[] DEFAULT_WINDOWS_PATHS = {
    "C:\\Program Files\\Mercurial",
    "C:\\Program Files (x86)\\Mercurial",
    "C:\\cygwin\\bin"};
  private static final @NonNls String[] DEFAULT_UNIX_PATHS = {
    "/usr/local/bin",
    "/usr/bin",
    "/opt/local/bin",
    "/opt/bin",
    "/usr/local/mercurial"};
  private static final @NonNls String DEFAULT_WINDOWS_HG = "hg.exe";
  private static final @NonNls String DEFAULT_UNIX_HG = "hg";

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

  /**
   * @return the default executable name depending on the platform
   */
  private static @NotNull String identifyDefaultHgExecutable() {
    File hgExecutableFromPath = PathEnvironmentVariableUtil.findInPath(SystemInfo.isWindows ? DEFAULT_WINDOWS_HG : DEFAULT_UNIX_HG,
                                                                       PathEnvironmentVariableUtil.getPathVariableValue(),
                                                                       null);
    if (hgExecutableFromPath != null) {
      return hgExecutableFromPath.getPath();
    }

    String[] paths;
    String programName;
    if (SystemInfo.isWindows) {
      programName = DEFAULT_WINDOWS_HG;
      paths = DEFAULT_WINDOWS_PATHS;
    }
    else {
      programName = DEFAULT_UNIX_HG;
      paths = DEFAULT_UNIX_PATHS;
    }

    for (String p : paths) {
      Path programPath = Paths.get(p, programName);
      if (Files.isExecutable(programPath)) {
        return programPath.toAbsolutePath().toString();
      }
    }
    // otherwise, take the first variant and hope it's in $PATH
    return programName;
  }
}
