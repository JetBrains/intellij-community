// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * Stores predefined and custom (user-defined) path variables. Path variables are used to convert paths from absolute to portable form and
 * vice versa. It allows us to reuse project configuration files on different machines.
 * <p>
 * In order to make a path (or URL) portable the serialization subsystem replaces its prefix by name of a corresponding path variable.
 * There are {@link #getSystemMacroNames() predefined path variables} and also it's possible to specify {@link #getUserMacroNames() custom path variables}.
 * </p>
 */
public abstract class PathMacros {
  public static PathMacros getInstance() {
    return ServiceManager.getService(PathMacros.class);
  }

  @NotNull
  public abstract Set<String> getAllMacroNames();

  @Nullable
  public abstract String getValue(@NotNull String name);

  public abstract void setMacro(@NotNull String name, @Nullable String value);

  /**
   * Obsolete macros that are to be removed gently from the project files. They can be read, but not written again. Not persisted
   */
  public abstract void addLegacyMacro(@NotNull String name, @NotNull String value);

  public abstract void removeMacro(@NotNull String name);

  public abstract Set<String> getUserMacroNames();

  public abstract Set<String> getSystemMacroNames();

  public abstract Collection<String> getIgnoredMacroNames();

  public abstract void setIgnoredMacroNames(@NotNull final Collection<String> names);

  public abstract void addIgnoredMacro(@NotNull final String name);

  public abstract boolean isIgnoredMacroName(@NotNull final String macro);

  public abstract void removeAllMacros();

  public abstract Collection<String> getLegacyMacroNames();
}
