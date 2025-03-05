// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import org.jetbrains.annotations.NotNull;

public class UsageAdapter implements Usage {
  @Override
  public @NotNull UsagePresentation getPresentation() {
    throw new IllegalAccessError();
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public FileEditorLocation getLocation() {
    return null;
  }

  @Override
  public void selectInEditor() {

  }

  @Override
  public void highlightInEditor() {

  }
}
