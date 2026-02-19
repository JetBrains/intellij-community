// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion;

import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public class ClosureParameterInfo {

  private final String myName;

  private final @Nullable String myType;

  public ClosureParameterInfo(@Nullable String type, String name) {
    myName = name;
    myType = type;
  }

  public String getName() {
    return myName;
  }

  public @Nullable String getType() {
    return myType;
  }
}
