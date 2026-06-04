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
   * Indicates that this process is running in a frontend mode (JetBrains Client).
   * It doesn't perform heavy tasks like code analysis and takes necessary information from a separate backend process
   */
  public static final ProductMode FRONTEND = new ProductMode("frontend");
  /**
   * Indicates that this process is running in a backend mode and serves as a remote development host.
   * It doesn't show the UI to the user directly, a separate process is responsible for this.
   */
  public static final ProductMode BACKEND = new ProductMode("backend");
  /**
   * Indicates that this process is running in a light mode - a minimalistic self-sufficient frontend IDE.
   */
  @ApiStatus.Internal
  public static final ProductMode LIGHT = new ProductMode("light");
  /**
   * Indicates that this process is running in a light mode with an established rd connection.
   * This is a temporary mode which appears during transition from the "light" to "frontend" mode.
   */
  @ApiStatus.Internal
  public static final ProductMode LIGHT_WITH_RD_CONNECTION = new ProductMode("light_with_rd_connection");

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
      case "light": return LIGHT;
      case "light_with_rd_connection": return LIGHT_WITH_RD_CONNECTION;
      default: return null;
    }
  }
}
