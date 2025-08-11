// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LocallyDeletedChange {
  private final @NotNull String myPresentableUrl;
  private final @NotNull FilePath myPath;

  public LocallyDeletedChange(@NotNull FilePath path) {
    myPath = path;
    myPresentableUrl = myPath.getPresentableUrl();
  }

  public @NotNull FilePath getPath() {
    return myPath;
  }

  public @Nullable Icon getAddIcon() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocallyDeletedChange that = (LocallyDeletedChange)o;

    return myPresentableUrl.equals(that.myPresentableUrl);
  }

  @Override
  public int hashCode() {
    return myPresentableUrl.hashCode();
  }

  public @NotNull @NlsSafe String getPresentableUrl() {
    return myPresentableUrl;
  }

  public @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
    return null;
  }

  @Override
  public @Nls String toString() {
    return myPath.getPath();
  }
}
