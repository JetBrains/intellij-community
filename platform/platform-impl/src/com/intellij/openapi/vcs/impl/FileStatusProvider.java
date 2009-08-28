package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.editor.Document;

/**
 * @author yole
 */
public interface FileStatusProvider {
  FileStatus getFileStatus(final VirtualFile virtualFile);
  void refreshFileStatusFromDocument(final VirtualFile file, final Document doc);
}