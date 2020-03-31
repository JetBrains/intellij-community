// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Usage extends Navigatable {
  Usage[] EMPTY_ARRAY = new Usage[0];

  @NotNull
  UsagePresentation getPresentation();
  boolean isValid();
  boolean isReadOnly();

  @Nullable
  FileEditorLocation getLocation();

  void selectInEditor();
  void highlightInEditor();
}
