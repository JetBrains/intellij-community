package com.intellij.cvsSupport2.javacvsImpl;

import com.intellij.openapi.vfs.VirtualFile;

public interface ProjectContentInfoProvider {
  boolean fileIsUnderProject(VirtualFile file);
}
