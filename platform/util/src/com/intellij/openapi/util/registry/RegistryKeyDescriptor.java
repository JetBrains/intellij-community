// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.registry;

import org.jetbrains.annotations.NotNull;

class RegistryKeyDescriptor {
  @NotNull private final String myName;
  @NotNull private final String myDefaultValue;
  @NotNull private final String myDescription;
  private final boolean myRestartRequired;

  RegistryKeyDescriptor(@NotNull String name, @NotNull String description, @NotNull String defaultValue,
                               boolean restartRequired) {
    myName = name;
    myDefaultValue = defaultValue;
    myDescription = description;
    myRestartRequired = restartRequired;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public String getDefaultValue() {
    return myDefaultValue;
  }

  public boolean isRestartRequired() {
    return myRestartRequired;
  }
}
