package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LocallyDeletedChange {
  private final String myPresentableUrl;
  private final FilePath myPath;

  public LocallyDeletedChange(@NotNull final FilePath path) {
    myPath = path;
    myPresentableUrl = myPath.getPresentableUrl();
  }

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

    if (!myPresentableUrl.equals(that.myPresentableUrl)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPresentableUrl.hashCode();
  }

  public String getPresentableUrl() {
    return myPresentableUrl;
  }

  @Nullable
  public String getDescription() {
    return null;
  }
}
