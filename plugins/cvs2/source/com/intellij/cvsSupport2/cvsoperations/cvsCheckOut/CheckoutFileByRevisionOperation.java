package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;

/**
 * author: lesya
 */
public class CheckoutFileByRevisionOperation extends CheckoutFileOperation{
  public CheckoutFileByRevisionOperation(VirtualFile parent, String fileName, String revision, boolean makeNewFilesReadOnly) {
    super(parent, new SimpleRevision(revision), fileName, makeNewFilesReadOnly);
  }
}
