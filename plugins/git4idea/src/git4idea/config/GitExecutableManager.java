// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import org.jetbrains.annotations.NotNull;

/**
 * Manager for "current git executable".
 * Allows to get a path to git executable.
 */
//TODO: move git version related stuff here
public class GitExecutableManager {
  public static GitExecutableManager getInstance() {
    return ServiceManager.getService(GitExecutableManager.class);
  }

  @NotNull private final GitVcsApplicationSettings myApplicationSettings;
  @NotNull private final AtomicNotNullLazyValue<String> myDetectedExecutable;

  public GitExecutableManager(@NotNull GitVcsApplicationSettings applicationSettings) {
    myApplicationSettings = applicationSettings;
    myDetectedExecutable = AtomicNotNullLazyValue.createValue(new GitExecutableDetector()::detect);
  }

  @NotNull
  public String getPathToGit() {
    String path = myApplicationSettings.getSavedPathToGit();
    return path == null ? getDetectedExecutable() : path;
  }

  @NotNull
  public String getPathToGit(@NotNull Project project) {
    String path = GitVcsSettings.getInstance(project).getPathToGit();
    return path == null ? getPathToGit() : path;
  }

  @NotNull
  public String getDetectedExecutable() {
    return myDetectedExecutable.getValue();
  }
}
