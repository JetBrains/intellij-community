package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;

/**
 * author: lesya
 */
public interface ReceivedFileProcessor {
  ReceivedFileProcessor DEFAULT = new ReceivedFileProcessor() {
    public boolean shouldProcess(VirtualFile virtualFile, File targetFile) {
      return true;
    }
  };

  boolean shouldProcess(VirtualFile virtualFile, File targetFile) throws IOException;
}
