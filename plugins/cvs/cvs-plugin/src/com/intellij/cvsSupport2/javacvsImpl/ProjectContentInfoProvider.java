package com.intellij.cvsSupport2.javacvsImpl;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public interface ProjectContentInfoProvider {
  boolean fileIsUnderProject(VirtualFile file);
  boolean fileIsUnderProject(File file);
}
