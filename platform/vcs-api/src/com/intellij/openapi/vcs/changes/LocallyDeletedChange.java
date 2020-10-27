// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LocallyDeletedChange {
  @NotNull private final String myPresentableUrl;
  @NotNull private final FilePath myPath;

  public LocallyDeletedChange(@NotNull FilePath path) {
    myPath = path;
    myPresentableUrl = myPath.getPresentableUrl();
  }

  @NotNull
  public FilePath getPath() {
    return myPath;
  }

  @Nullable
  public Icon getAddIcon() {
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

  @NotNull
  @NlsSafe
  public String getPresentableUrl() {
    return myPresentableUrl;
  }

  @Nullable
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String getDescription() {
    return null;
  }

  @Nls
  public String toString() {
    return myPath.getPath();
  }
}
