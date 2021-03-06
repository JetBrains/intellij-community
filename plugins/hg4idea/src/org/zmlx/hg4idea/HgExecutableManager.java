// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class HgExecutableManager {
  public static HgExecutableManager getInstance() {
    return ApplicationManager.getApplication().getService(HgExecutableManager.class);
  }

  @NonNls private static final String[] DEFAULT_WINDOWS_PATHS = {
    "C:\\Program Files\\Mercurial",
    "C:\\Program Files (x86)\\Mercurial",
    "C:\\cygwin\\bin"};
  @NonNls private static final String[] DEFAULT_UNIX_PATHS = {
    "/usr/local/bin",
    "/usr/bin",
    "/opt/local/bin",
    "/opt/bin",
    "/usr/local/mercurial"};
  @NonNls private static final String DEFAULT_WINDOWS_HG = "hg.exe";
  @NonNls private static final String DEFAULT_UNIX_HG = "hg";

  @NotNull private final NotNullLazyValue<String> myDetectedExecutable;

  public HgExecutableManager() {
    myDetectedExecutable = NotNullLazyValue.atomicLazy(HgExecutableManager::identifyDefaultHgExecutable);
  }

  @NotNull
  public String getHgExecutable() {
    String path = ApplicationManager.getApplication().getService(HgGlobalSettings.class).getHgExecutable();
    return path == null ? getDefaultExecutable() : path;
  }

  @NotNull
  public String getHgExecutable(@NotNull Project project) {
    HgProjectSettings projectSettings = HgProjectSettings.getInstance(project);
    if (!projectSettings.isHgExecutableOverridden()) return getHgExecutable();

    String path = projectSettings.getHgExecutable();
    return path == null ? getDefaultExecutable() : path;
  }

  @NotNull
  public String getDefaultExecutable() {
    return myDetectedExecutable.getValue();
  }

  /**
   * @return the default executable name depending on the platform
   */
  @NotNull
  private static String identifyDefaultHgExecutable() {
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
