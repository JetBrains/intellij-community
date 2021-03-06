// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public abstract class ConfigurationErrorDescription {
  private final String myElementName;
  private final @DetailedDescription String myDescription;
  private final ConfigurationErrorType myErrorType;

  protected ConfigurationErrorDescription(@NotNull String elementName, @DetailedDescription @NotNull String description, @NotNull ConfigurationErrorType errorType) {
    myElementName = elementName;
    myErrorType = errorType;
    myDescription = description;
  }

  public @NlsSafe @NotNull String getElementName() {
    return myElementName;
  }

  public @NotNull ConfigurationErrorType getErrorType() {
    return myErrorType;
  }

  public @DetailedDescription String getDescription() {
    return myDescription;
  }

  public abstract void ignoreInvalidElement();

  public abstract @DetailedDescription @NotNull String getIgnoreConfirmationMessage();

  public boolean isValid() {
    return true;
  }
}
