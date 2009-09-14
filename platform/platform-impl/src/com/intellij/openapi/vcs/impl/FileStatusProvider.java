package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface FileStatusProvider {

  ExtensionPointName<FileStatusProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcs.fileStatusProvider");
  
  FileStatus getFileStatus(final VirtualFile virtualFile);
  void refreshFileStatusFromDocument(final VirtualFile file, final Document doc);
}