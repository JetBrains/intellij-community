// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public abstract class AntMessageCustomizer {

  public static final ExtensionPointName<AntMessageCustomizer> EP_NAME = ExtensionPointName.create("AntSupport.AntMessageCustomizer");

  public @Nullable AntMessage createCustomizedMessage(@Nls String text, @AntMessage.Priority int priority) {
    return null;
  }
}
