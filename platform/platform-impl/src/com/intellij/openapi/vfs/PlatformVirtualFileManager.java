// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class PlatformVirtualFileManager extends VirtualFileManagerImpl {
  @NotNull private final ManagingFS myManagingFS;

  public PlatformVirtualFileManager() {
    super(getVirtualFileSystems());

    myManagingFS = ManagingFS.getInstance();
  }

  @NotNull
  private static List<VirtualFileSystem> getVirtualFileSystems() {
    Application app = ApplicationManager.getApplication();
    @SuppressWarnings("deprecation")
    List<VirtualFileSystem> result = app instanceof ComponentManagerImpl
                                     ? app.getComponentInstancesOfType(VirtualFileSystem.class, true)
                                     : Collections.emptyList();
    if (!result.isEmpty()) {
      LOG.warn("Do not register file system as application component, instead, register as extension, for example:\n" +
               "<virtualFileSystem implementationClass=\"com.example.MyFileSystem\" key=\"myProtocol\" physical=\"true\"/>\n\n" + result);
    }
    return result;
  }

  @Override
  protected long doRefresh(boolean asynchronous, @Nullable Runnable postAction) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }

    // todo: get an idea how to deliver changes from local FS to jar fs before they go refresh
    RefreshSession session = RefreshQueue.getInstance().createSession(asynchronous, true, postAction);
    session.addAllFiles(myManagingFS.getRoots());
    session.launch();

    super.doRefresh(asynchronous, postAction);

    return session.getId();
  }

  @Override
  public long getModificationCount() {
    return myManagingFS.getModificationCount();
  }

  @Override
  public long getStructureModificationCount() {
    return myManagingFS.getStructureModificationCount();
  }

  @Override
  public VirtualFile findFileById(int id) {
    return myManagingFS.findFileById(id);
  }

  @NotNull
  @Override
  public CharSequence getVFileName(int nameId) {
    return FileNameCache.getVFileName(nameId);
  }

  @Override
  public int storeName(@NotNull String name) {
    return FileNameCache.storeName(name);
  }
}
