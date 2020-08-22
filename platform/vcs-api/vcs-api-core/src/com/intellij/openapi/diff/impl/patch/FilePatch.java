// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

public abstract class FilePatch {
  private String myBeforeName;
  private String myAfterName;
  @Nullable private String myBeforeVersionId;
  @Nullable private String myAfterVersionId;
  private String myBaseRevisionText;
  // store file mode in 6 digit format a.e. 100655, -1 means file mode was not changed in the patch
  private int myNewFileMode = -1;

  public String getBeforeName() {
    return myBeforeName;
  }

  public String getAfterName() {
    return myAfterName;
  }

  public String getBeforeFileName() {
    String[] pathNameComponents = myBeforeName.split("/");
    return pathNameComponents [pathNameComponents.length-1];
  }

  public String getAfterFileName() {
    String[] pathNameComponents = myAfterName.split("/");
    return pathNameComponents [pathNameComponents.length-1];
  }

  public void setBeforeName(final String fileName) {
    myBeforeName = fileName;
  }

  public void setAfterName(final String fileName) {
    myAfterName = fileName;
  }

  @Nullable
  public String getBeforeVersionId() {
    return myBeforeVersionId;
  }

  public void setBeforeVersionId(@Nullable final String beforeVersionId) {
    myBeforeVersionId = beforeVersionId;
  }

  @Nullable
  public String getAfterVersionId() {
    return myAfterVersionId;
  }

  public void setAfterVersionId(@Nullable final String afterVersionId) {
    myAfterVersionId = afterVersionId;
  }

  public String getAfterNameRelative(int skipDirs) {
    String[] components = myAfterName.split("/");
    return StringUtil.join(components, skipDirs, components.length, "/");
  }

  public String getBaseRevisionText() {
    return myBaseRevisionText;
  }

  public void setBaseRevisionText(String baseRevisionText) {
    myBaseRevisionText = baseRevisionText;
  }

  public abstract boolean isNewFile();

  public abstract boolean isDeletedFile();

  public int getNewFileMode() {
    return myNewFileMode;
  }

  public void setNewFileMode(int newFileMode) {
    myNewFileMode = newFileMode;
  }
}
