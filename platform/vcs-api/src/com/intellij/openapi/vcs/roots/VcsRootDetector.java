package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Interface for detecting VCS roots in the project.
 *
 * @author Nadya Zabrodina
 */
public interface VcsRootDetector {

  /**
   * Detect vcs roots for whole project
   */
  @NotNull
  Collection<VcsRoot> detect();

  /**
   * Detect vcs roots for startDir
   */
  @NotNull
  Collection<VcsRoot> detect(@Nullable VirtualFile startDir);

  /**
   * Returns the cached result of the previous call to {@link #detect()} if there was any, otherwise calls it and waits for the completion.
   */
  @NotNull
  Collection<VcsRoot> getOrDetect();
}
