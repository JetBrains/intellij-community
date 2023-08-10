// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.NotNull;

/**
 * Describes a part of the product in which a module may be used.
 * @see IncludedRuntimeModule#getScopes() 
 */
public final class RuntimeModuleScope {
  /**
   * The frontend part of a desktop IDE.
   */
  public static final RuntimeModuleScope IDE_FRONTEND = new RuntimeModuleScope("IDE_FRONTEND");
  /**
   * The backend part, it may be used in a desktop IDE or in a separate process. 
   */
  public static final RuntimeModuleScope BACKEND = new RuntimeModuleScope("BACKEND");
  /**
   * JPS build process (either standalone, or started from an IDE); probably this constant should be moved to Java plugin.
   */
  public static final RuntimeModuleScope JPS_BUILD = new RuntimeModuleScope("JPS_BUILD");
  
  private final String myId;

  private RuntimeModuleScope(@NotNull String id) {
    myId = id;
  }

  public @NotNull String getId() {
    return myId;
  }
}
