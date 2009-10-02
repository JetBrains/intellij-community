package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Convertor;

public class IntoSelfVirtualFileConvertor extends Convertor.IntoSelf<VirtualFile> {
  private static final IntoSelfVirtualFileConvertor ourInstance = new IntoSelfVirtualFileConvertor();

  public static IntoSelfVirtualFileConvertor getInstance() {
    return ourInstance;
  }
}
