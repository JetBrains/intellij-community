// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

public abstract class FilePatch {
  private @Nullable String myBeforeName;
  private @Nullable String myAfterName;
  private @Nullable String myBeforeVersionId;
  private @Nullable String myAfterVersionId;
  // store file mode in 6 digit format a.e. 100655, -1 means file mode was not changed in the patch
  private int myNewFileMode = -1;

  public @NlsSafe String getBeforeName() {
    return myBeforeName;
  }

  public @NlsSafe String getAfterName() {
    return myAfterName;
  }

  public @Nullable String getBeforeFileName() {
    if (myBeforeName == null) return null;
    String[] pathNameComponents = myBeforeName.split("/");
    return pathNameComponents[pathNameComponents.length - 1];
  }

  public @Nullable String getAfterFileName() {
    if (myAfterName == null) return null;
    String[] pathNameComponents = myAfterName.split("/");
    return pathNameComponents[pathNameComponents.length - 1];
  }

  public void setBeforeName(@Nullable String fileName) {
    myBeforeName = fileName;
  }

  public void setAfterName(@Nullable String fileName) {
    myAfterName = fileName;
  }

  public @Nullable @NlsSafe String getBeforeVersionId() {
    return myBeforeVersionId;
  }

  public void setBeforeVersionId(@Nullable @NlsSafe String beforeVersionId) {
    myBeforeVersionId = beforeVersionId;
  }

  public @Nullable @NlsSafe String getAfterVersionId() {
    return myAfterVersionId;
  }

  public void setAfterVersionId(@Nullable @NlsSafe String afterVersionId) {
    myAfterVersionId = afterVersionId;
  }

  public abstract boolean isNewFile();

  public abstract boolean isDeletedFile();

  public int getNewFileMode() {
    return myNewFileMode;
  }

  public void setNewFileMode(int newFileMode) {
    myNewFileMode = newFileMode;
  }

  @Override
  public String toString() {
    return "FilePatch{" +
           "myBeforeName='" + myBeforeName + '\'' +
           ", myAfterName='" + myAfterName + '\'' +
           ", myBeforeVersionId='" + myBeforeVersionId + '\'' +
           ", myAfterVersionId='" + myAfterVersionId + '\'' +
           '}';
  }
}
