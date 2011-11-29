package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public abstract class AntMessageCustomizer {

  public static final ExtensionPointName<AntMessageCustomizer> EP_NAME = ExtensionPointName.create("AntSupport.AntMessageCustomizer");

  @Nullable
  public AntMessage createCustomizedMessage(String text, int priority) {
    return null;
  }
}
