/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

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

  public abstract Set<String> getAllMacroNames();

  public abstract String getValue(String name);

  public abstract void setMacro(@NotNull String name, @NotNull String value);

  /**
   * Obsolete macros that are to be removed gently from the project files. They can be read, but not written again. Not persisted
   */
  public abstract void addLegacyMacro(@NotNull String name, @NotNull String value);

  public abstract void removeMacro(String name);

  public abstract Set<String> getUserMacroNames();

  public abstract Set<String> getSystemMacroNames();

  public abstract Collection<String> getIgnoredMacroNames();

  public abstract void setIgnoredMacroNames(@NotNull final Collection<String> names);

  public abstract void addIgnoredMacro(@NotNull final String name);

  public abstract boolean isIgnoredMacroName(@NotNull final String macro);

  public abstract void removeAllMacros();

  public abstract Collection<String> getLegacyMacroNames();
}
