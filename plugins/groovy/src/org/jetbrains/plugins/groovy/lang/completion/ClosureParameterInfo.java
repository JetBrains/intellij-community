// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public class ClosureParameterInfo {

  private final String myName;

  @Nullable
  private final String myType;

  public ClosureParameterInfo(@Nullable String type, String name) {
    myName = name;
    myType = type;
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public String getType() {
    return myType;
  }
}
