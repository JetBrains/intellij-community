// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HgExecutableManager {
  public static HgExecutableManager getInstance() {
    return ServiceManager.getService(HgExecutableManager.class);
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

  @NotNull private final HgGlobalSettings myGlobalSettings;
  @NotNull private final AtomicNotNullLazyValue<String> myDetectedExecutable;

  public HgExecutableManager(@NotNull HgGlobalSettings globalSettings) {
    myGlobalSettings = globalSettings;
    myDetectedExecutable = AtomicNotNullLazyValue.createValue(HgExecutableManager::identifyDefaultHgExecutable);
  }

  @NotNull
  public String getHgExecutable() {
    String path = myGlobalSettings.getHgExecutable();
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
