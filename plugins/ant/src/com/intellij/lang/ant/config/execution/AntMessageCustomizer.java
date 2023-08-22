package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public abstract class AntMessageCustomizer {

  public static final ExtensionPointName<AntMessageCustomizer> EP_NAME = ExtensionPointName.create("AntSupport.AntMessageCustomizer");

  @Nullable
  public AntMessage createCustomizedMessage(@Nls String text, @AntMessage.Priority int priority) {
    return null;
  }
}
