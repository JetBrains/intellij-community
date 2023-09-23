// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nullable;

public abstract class FilePatch {
  private @Nullable String myBeforeName;
  private @Nullable String myAfterName;
  @Nullable private String myBeforeVersionId;
  @Nullable private String myAfterVersionId;
  // store file mode in 6 digit format a.e. 100655, -1 means file mode was not changed in the patch
  private int myNewFileMode = -1;

  public String getBeforeName() {
    return myBeforeName;
  }

  public String getAfterName() {
    return myAfterName;
  }

  @Nullable
  public String getBeforeFileName() {
    if (myBeforeName == null) return null;
    String[] pathNameComponents = myBeforeName.split("/");
    return pathNameComponents[pathNameComponents.length - 1];
  }

  @Nullable
  public String getAfterFileName() {
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

  @Nullable
  public @NlsSafe String getBeforeVersionId() {
    return myBeforeVersionId;
  }

  public void setBeforeVersionId(@Nullable @NlsSafe String beforeVersionId) {
    myBeforeVersionId = beforeVersionId;
  }

  @Nullable
  public @NlsSafe String getAfterVersionId() {
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
