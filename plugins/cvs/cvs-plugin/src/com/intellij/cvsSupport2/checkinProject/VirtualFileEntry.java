package com.intellij.cvsSupport2.checkinProject;

import com.intellij.openapi.vfs.*;
import org.netbeans.lib.cvsclient.admin.*;

/**
 * author: lesya
 */
public class VirtualFileEntry {
  private final VirtualFile myVirtualFile;
  private final Entry myEntry;

  public VirtualFileEntry(VirtualFile virtualFile, Entry entry) {
    myVirtualFile = virtualFile;
    myEntry = entry;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public Entry getEntry() {
    return myEntry;
  }
}
