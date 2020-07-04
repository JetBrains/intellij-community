// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public final class AsyncEventSupport {
  private static final Logger LOG = Logger.getInstance(AsyncEventSupport.class);

  @ApiStatus.Internal
  public static final ExtensionPointName<AsyncFileListener> EP_NAME = new ExtensionPointName<>("com.intellij.vfs.asyncListener");
  private static boolean ourSuppressAppliers;

  public static void startListening() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      Pair<List<? extends VFileEvent>, List<AsyncFileListener.ChangeApplier>> appliersFromBefore;

      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
        if (ourSuppressAppliers) return;
        List<AsyncFileListener.ChangeApplier> appliers = runAsyncListeners(events);
        appliersFromBefore = Pair.create(events, appliers);
        beforeVfsChange(appliers);
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        if (ourSuppressAppliers) return;
        List<AsyncFileListener.ChangeApplier> appliers = appliersFromBefore != null && appliersFromBefore.first.equals(events)
                                                         ? appliersFromBefore.second
                                                         : runAsyncListeners(events);
        appliersFromBefore = null;
        afterVfsChange(appliers);
      }
    });
  }

  @NotNull
  static List<AsyncFileListener.ChangeApplier> runAsyncListeners(@NotNull List<? extends VFileEvent> events) {
    if (events.isEmpty()) return Collections.emptyList();

    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing " + events);
    }

    List<AsyncFileListener.ChangeApplier> appliers = new ArrayList<>();
    List<AsyncFileListener> allListeners = new ArrayList<>(EP_NAME.getExtensionList());
    ((VirtualFileManagerImpl)VirtualFileManager.getInstance()).addAsyncFileListenersTo(allListeners);
    for (AsyncFileListener listener : allListeners) {
      ProgressManager.checkCanceled();
      long startNs = System.nanoTime();
      boolean canceled = false;
      try {
        ReadAction.run(() -> ContainerUtil.addIfNotNull(appliers, listener.prepareChange(events)));
      }
      catch (ProcessCanceledException e) {
        canceled = true;
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      finally {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        if (elapsedMs > 10_000) {
          LOG.warn(listener + " took too long (" + elapsedMs + "ms) on " + events.size() + " events" + (canceled ? ", canceled" : ""));
        }
      }
    }
    return appliers;
  }

  private static void beforeVfsChange(List<? extends AsyncFileListener.ChangeApplier> appliers) {
    for (AsyncFileListener.ChangeApplier applier : appliers) {
      PingProgress.interactWithEdtProgress();
      try {
        applier.beforeVfsChange();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private static void afterVfsChange(@NotNull List<? extends AsyncFileListener.ChangeApplier> appliers) {
    for (AsyncFileListener.ChangeApplier applier : appliers) {
      PingProgress.interactWithEdtProgress();
      try {
        applier.afterVfsChange();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  static void processEventsFromRefresh(@NotNull List<? extends VFileEvent> events,
                                       @Nullable List<? extends AsyncFileListener.ChangeApplier> appliers) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (appliers != null) {
      beforeVfsChange(appliers);
      ourSuppressAppliers = true;
    }
    try {
      PersistentFS.getInstance().processEvents(events);
    }
    finally {
      ourSuppressAppliers = false;
    }
    if (appliers != null) {
      afterVfsChange(appliers);
    }
  }
}
