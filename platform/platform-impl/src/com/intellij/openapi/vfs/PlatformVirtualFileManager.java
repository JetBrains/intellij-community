/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlatformVirtualFileManager extends VirtualFileManagerImpl {
  @NotNull private final ManagingFS myManagingFS;

  public PlatformVirtualFileManager(@NotNull VirtualFileSystem[] fileSystems, @NotNull MessageBus bus, @NotNull ManagingFS managingFS) {
    super(fileSystems, bus);
    myManagingFS = managingFS;
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
}
