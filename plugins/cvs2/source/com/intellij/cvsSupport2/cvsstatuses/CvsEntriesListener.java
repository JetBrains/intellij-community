package com.intellij.cvsSupport2.cvsstatuses;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * author: lesya
 */

public interface CvsEntriesListener {
  void entriesChanged(VirtualFile parent);

  void entryChanged(VirtualFile file);
}
