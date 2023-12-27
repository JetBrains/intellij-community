// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class LibraryTablePresentation {

  public abstract @NotNull
  @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName(boolean plural);

  public abstract @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription();

  public abstract @NotNull String getLibraryTableEditorTitle();

}
