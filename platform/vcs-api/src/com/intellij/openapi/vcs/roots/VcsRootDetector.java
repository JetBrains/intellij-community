package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Interface for detecting VCS roots in the project.
 *
 * @see VcsRootChecker
 */
public interface VcsRootDetector {
  static VcsRootDetector getInstance(@NotNull Project project) {
    return project.getService(VcsRootDetector.class);
  }

  /**
   * Detect vcs roots for whole project
   */
  @NotNull
  @Unmodifiable Collection<VcsRoot> detect();

  /**
   * Detect vcs roots for startDir
   */
  @NotNull
  @Unmodifiable Collection<VcsRoot> detect(@Nullable VirtualFile startDir);

  /**
   * Returns the cached result of the previous call to {@link #detect()} if there was any, otherwise calls it and waits for the completion.
   */
  @NotNull
  @Unmodifiable
  Collection<VcsRoot> getOrDetect();
}
