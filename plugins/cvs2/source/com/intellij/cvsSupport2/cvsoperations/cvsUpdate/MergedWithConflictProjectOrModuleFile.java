package com.intellij.cvsSupport2.cvsoperations.cvsUpdate;

import com.intellij.openapi.vfs.VirtualFile;

public class MergedWithConflictProjectOrModuleFile {
  private final VirtualFile myOriginal;
  private boolean myShouldBeCheckedOut = false;

  public MergedWithConflictProjectOrModuleFile(VirtualFile original) {
    myOriginal = original;
  }

  public VirtualFile getOriginal() {
    return myOriginal;
  }

  public void setShouldBeCheckedOut() {
    myShouldBeCheckedOut = true;
  }

  public boolean shouldBeCheckedOut() {
    return myShouldBeCheckedOut;
  }
}