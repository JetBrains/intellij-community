// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a mode in which a product may be started.
 * TODO: reuse inside {@link com.intellij.idea.AppMode}?
 */
public final class ProductMode {
  /**
   * Indicates that this process performs all necessary tasks to provide smart features itself. This is the default mode for all IDEs. 
   */
  public static final ProductMode MONOLITH = new ProductMode("monolith");
  /**
   * Indicates that this process doesn't perform heavy tasks like code analysis, and takes necessary information from another process.
   * Currently, this is used by JetBrains Client process connected to a remote development host or CodeWithMe session.
   */
  public static final ProductMode FRONTEND = new ProductMode("frontend");
  /**
   * Indicates that this process doesn't perform heavy tasks like code analysis and takes necessary information from another process.
   * Currently, this is used by an IDE running as a remote development host.
   */
  public static final ProductMode BACKEND = new ProductMode("backend");

  private final String myId;

  private ProductMode(@NotNull @NonNls String id) {
    myId = id;
  }

  public @NotNull @NonNls String getId() {
    return myId;
  }

  @ApiStatus.Internal
  public static @Nullable ProductMode findById(@NotNull @NonNls String id) {
    switch (id) {
      case "monolith": return MONOLITH;
      case "frontend": return FRONTEND;
      case "backend": return BACKEND;
      default: return null;
    }
  }
}
