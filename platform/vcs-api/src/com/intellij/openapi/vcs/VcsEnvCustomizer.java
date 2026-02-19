// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.execution.wsl.WSLCommandLineOptions;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Allows to modify ENV that is used when invoking external VCS commands.
 * <p>
 * For example, it can be used to pass Project-specific variables (such as configured python venv folder)
 * to the "git pre-commit hooks".
 */
public abstract class VcsEnvCustomizer {
  public static final ExtensionPointName<VcsEnvCustomizer> EP_NAME =
    ExtensionPointName.create("com.intellij.vcs.envCustomizer");

  /**
   * May adjust environment for VCS commands
   *
   * @param envs mutable map of environment variables
   */
  public abstract void customizeCommandAndEnvironment(@Nullable Project project,
                                                      @NotNull Map<String, String> envs,
                                                      @NotNull VcsExecutableContext context);

  /**
   * @return configurable for customizer-specific options
   */
  public @Nullable UnnamedConfigurable getConfigurable(@Nullable Project project) {
    return null;
  }


  public static class VcsExecutableContext {
    private final AbstractVcs myVcs;
    private final VirtualFile myRoot;
    private final ExecutableType myType;

    private final @Nullable WSLCommandLineOptions myWslOptions;

    public VcsExecutableContext(@Nullable AbstractVcs vcs, @Nullable VirtualFile vcsRoot, @NotNull ExecutableType type) {
      myVcs = vcs;
      myRoot = vcsRoot;
      myType = type;
      myWslOptions = type == VcsEnvCustomizer.ExecutableType.WSL ? new WSLCommandLineOptions() : null;
    }

    public @Nullable AbstractVcs getVcs() {
      return myVcs;
    }

    public @Nullable VirtualFile getRoot() {
      return myRoot;
    }

    public @NotNull ExecutableType getType() {
      return myType;
    }

    public @Nullable WSLCommandLineOptions getWslOptions() {
      return myWslOptions;
    }
  }

  public enum ExecutableType {LOCAL, WSL}
}
