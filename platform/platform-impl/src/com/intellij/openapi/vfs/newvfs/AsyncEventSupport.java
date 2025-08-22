// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VfsThreadingUtil;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class AsyncEventSupport {
  private static final Logger LOG = Logger.getInstance(AsyncEventSupport.class);

  @ApiStatus.Internal
  public static final ExtensionPointName<AsyncFileListener> EP_NAME = new ExtensionPointName<>("com.intellij.vfs.asyncListener");

  @ApiStatus.Internal
  public static final ExtensionPointName<AsyncFileListener> EP_NAME_BACKGROUNDABLE =
    new ExtensionPointName<>("com.intellij.vfs.asyncListenerBackgroundable");

  // VFS events could be fired with any unpredictable nesting.
  // One may fire new events (for example, using `syncPublisher()`) while processing current events in `before()` or `after()`.
  // So, we cannot rely on listener's execution and nesting order in this case and have to explicitly mark events
  // that are supposed to be processed asynchronously.
  private static final @NotNull Set<List<? extends VFileEvent>> asyncProcessedEvents =
    CollectionFactory.createCustomHashingStrategySet(HashingStrategy.identity());
  private static final @NotNull Map<List<? extends VFileEvent>, List<AsyncFileListener.ChangeApplier>> appliers =
    CollectionFactory.createSmallMemoryFootprintMap(1);

  public static void startListening() {
    Application app = ApplicationManager.getApplication();
    Disposer.register(app, AsyncEventSupport::ensureAllEventsProcessed);

    app.getMessageBus().simpleConnect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (asyncProcessedEvents.contains(events)) {
          return;
        }
        List<AsyncFileListener.ChangeApplier> appliers = runAsyncListeners(events);
        AsyncEventSupport.appliers.put(events, appliers);
        beforeVfsChange(appliers);
      }

      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (asyncProcessedEvents.contains(events)) {
          return;
        }
        List<AsyncFileListener.ChangeApplier> appliers = AsyncEventSupport.appliers.remove(events);
        if (appliers == null || appliers.isEmpty()) {
          return;
        }
        afterVfsChange(appliers);
      }
    });
  }

  private static void ensureAllEventsProcessed() {
    LOG.assertTrue(asyncProcessedEvents.isEmpty(), "Some VFS events were not properly processed " + asyncProcessedEvents);
    LOG.assertTrue(appliers.isEmpty(), "Some VFS events were not processed after VFS change performed " + appliers);
  }

  static @NotNull List<AsyncFileListener.ChangeApplier> runAsyncListeners(@NotNull List<? extends VFileEvent> events) {
    if (events.isEmpty()) return Collections.emptyList();

    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing " + events);
    }

    List<AsyncFileListener.ChangeApplier> appliers = new ArrayList<>();
    List<AsyncFileListener.ChangeApplier> appliersBackgroundable = new ArrayList<>();
    List<AsyncFileListener> allListeners =
      ((VirtualFileManagerImpl)VirtualFileManager.getInstance()).withAsyncFileListeners(EP_NAME.getExtensionList());
    List<AsyncFileListener> allListenersBackgroundable =
      ((VirtualFileManagerImpl)VirtualFileManager.getInstance()).withAsyncFileListenersBackgroundable(EP_NAME_BACKGROUNDABLE.getExtensionList());
    collectAppliers(events, allListeners, appliers);
    collectAppliers(events, allListenersBackgroundable, appliersBackgroundable);
    appliersBackgroundable.add(new AsyncFileListener.ChangeApplier() {
      @Override
      public void beforeVfsChange() {
        if (appliers.isEmpty()) {
          return;
        }
        VfsThreadingUtil.runActionOnEdtRegardlessOfCurrentThread(() -> {
          invokeAppliers(appliers, AsyncFileListener.ChangeApplier::beforeVfsChange);
        });
      }

      @Override
      public void afterVfsChange() {
        if (appliers.isEmpty()) {
          return;
        }
        VfsThreadingUtil.runActionOnEdtRegardlessOfCurrentThread(() -> {
          invokeAppliers(appliers, AsyncFileListener.ChangeApplier::afterVfsChange);
        });
      }
    });
    return appliersBackgroundable;
  }

  private static void collectAppliers(@NotNull List<? extends VFileEvent> events,
                                 List<AsyncFileListener> allListeners,
                                 List<AsyncFileListener.ChangeApplier> appliers) {
    for (AsyncFileListener listener : allListeners) {
      ProgressManager.checkCanceled();
      long startNs = System.nanoTime();
      boolean canceled = false;
      try {
        ApplicationManager.getApplication().runReadAction(() -> ContainerUtil.addIfNotNull(appliers, listener.prepareChange(events)));
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
  }

  public static void markAsynchronouslyProcessedEvents(@NotNull List<? extends VFileEvent> events) {
    asyncProcessedEvents.add(events);
  }

  public static void unmarkAsynchronouslyProcessedEvents(@NotNull List<? extends VFileEvent> events) {
    LOG.assertTrue(asyncProcessedEvents.remove(events));
  }

  private static void beforeVfsChange(@NotNull List<AsyncFileListener.ChangeApplier> appliers) {
    invokeAppliers(appliers, AsyncFileListener.ChangeApplier::beforeVfsChange);
  }

  private static void invokeAppliers(List<AsyncFileListener.ChangeApplier> appliers, Consumer<AsyncFileListener.ChangeApplier> consumer) {
    for (AsyncFileListener.ChangeApplier applier : appliers) {
      PingProgress.interactWithEdtProgress();
      try {
        consumer.accept(applier);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private static void afterVfsChange(@NotNull List<AsyncFileListener.ChangeApplier> appliers) {
    invokeAppliers(appliers, AsyncFileListener.ChangeApplier::afterVfsChange);
  }

  @RequiresWriteLock
  static void processEventsFromRefresh(@NotNull List<CompoundVFileEvent> events,
                                       @NotNull List<AsyncFileListener.ChangeApplier> appliers,
                                       boolean asyncProcessing) {
    beforeVfsChange(appliers);
    try {
      ((PersistentFSImpl)PersistentFS.getInstance()).processEventsImpl(events, asyncProcessing);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    finally {
      afterVfsChange(appliers);
    }
  }
}
