package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.vfs.DeprecatedVirtualFile;

/**
 * @author nik
 */
public abstract class HttpVirtualFile extends DeprecatedVirtualFile {
  public abstract RemoteFileInfo getFileInfo();
}
