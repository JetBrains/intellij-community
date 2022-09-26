// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ConfigurationErrorDescription {

  private final String myElementName;
  private final @DetailedDescription String myDescription;

  protected ConfigurationErrorDescription(@NotNull String elementName, @DetailedDescription @NotNull String description) {
    myElementName = elementName;
    myDescription = description;
  }

  public final @NlsSafe @NotNull String getElementName() {
    return myElementName;
  }

  public abstract @NotNull ConfigurationErrorType getErrorType();

  public final @DetailedDescription String getDescription() {
    return myDescription;
  }

  public abstract void ignoreInvalidElement();

  public abstract @DetailedDescription @NotNull String getIgnoreConfirmationMessage();

  public boolean isValid() {
    return true;
  }

  public @Nullable @NonNls String getImplementationName() {
    return null;
  }
}
