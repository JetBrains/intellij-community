// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.AfterEventShouldBeFiredBeforeOtherListeners;
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
import kotlin.Pair;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.openapi.diagnostic.LoggerKt.rethrowControlFlowException;

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
  // So, we cannot rely on the listener's execution and nesting order in this case and have to explicitly mark events
  // that are supposed to be processed asynchronously.
  private static final @NotNull Set<List<? extends VFileEvent>> asyncProcessedEvents =
    CollectionFactory.createCustomHashingStrategySet(HashingStrategy.identity());
  private static final @NotNull Map<List<? extends VFileEvent>, ChangeAppliers> appliers =
    CollectionFactory.createSmallMemoryFootprintMap(1);

  public static void startListening() {
    var app = ApplicationManager.getApplication();
    Disposer.register(app, AsyncEventSupport::ensureAllEventsProcessed);

    app.getMessageBus().simpleConnect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (asyncProcessedEvents.contains(events)) {
          return;
        }
        var appliers = runAsyncListeners(events);
        AsyncEventSupport.appliers.put(events, appliers);
        beforeVfsChange(appliers);
      }

      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (asyncProcessedEvents.contains(events)) {
          return;
        }
        var appliers = AsyncEventSupport.appliers.remove(events);
        if (appliers == null || (appliers.edtAppliers.isEmpty() && appliers.backgroundAppliers.isEmpty())) {
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

  @ApiStatus.Internal
  public record ChangeAppliers(
    @NotNull List<AsyncFileListener.ChangeApplier> edtAppliers,
    @NotNull List<AsyncFileListener.ChangeApplier> backgroundAppliers
  ) {
    public static final ChangeAppliers EMPTY = new ChangeAppliers(Collections.emptyList(), Collections.emptyList());

    /**
     * Splits {@link ChangeAppliers} into two parts: the first one where {@param predicate} returns true,
     * and the second one where {@param predicate} returns false.
     */
    @NotNull Pair<ChangeAppliers, ChangeAppliers> split(Predicate<AsyncFileListener.ChangeApplier> predicate) {
      if (edtAppliers.isEmpty() && backgroundAppliers.isEmpty()) {
        return new Pair<>(EMPTY, EMPTY);
      }
      var edtPartition = CollectionsKt.partition(edtAppliers, predicate::test);
      var bgPartition = CollectionsKt.partition(backgroundAppliers, predicate::test);
      return new Pair<>(
        new ChangeAppliers(edtPartition.getFirst(), bgPartition.getFirst()),
        new ChangeAppliers(edtPartition.getSecond(), bgPartition.getSecond())
      );
    }
  }

  static @NotNull AsyncEventSupport.ChangeAppliers runAsyncListeners(@NotNull List<? extends VFileEvent> events) {
    if (events.isEmpty()) return ChangeAppliers.EMPTY;

    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing " + events);
    }

    var appliersEdt = new ArrayList<AsyncFileListener.ChangeApplier>();
    var appliersBackgroundable = new ArrayList<AsyncFileListener.ChangeApplier>();
    var vfm = (VirtualFileManagerImpl)VirtualFileManager.getInstance();
    collectAppliers(events, vfm.withAsyncFileListeners(EP_NAME.getExtensionList()), appliersEdt);
    collectAppliers(events, vfm.withAsyncFileListenersBackgroundable(EP_NAME_BACKGROUNDABLE.getExtensionList()), appliersBackgroundable);
    return new ChangeAppliers(appliersEdt, appliersBackgroundable);
  }

  private static void collectAppliers(
    List<? extends VFileEvent> events,
    List<AsyncFileListener> listeners,
    List<AsyncFileListener.ChangeApplier> appliers
  ) {
    for (var listener : listeners) {
      ProgressManager.checkCanceled();
      var startNs = System.nanoTime();
      var canceled = false;
      try {
        ReadAction.runBlocking(() -> ContainerUtil.addIfNotNull(appliers, listener.prepareChange(events)));
      }
      catch (ProcessCanceledException e) {
        canceled = true;
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      finally {
        var elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
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

  private static void beforeVfsChange(@NotNull AsyncEventSupport.ChangeAppliers appliers) {
    invokeAppliers(appliers, AsyncFileListener.ChangeApplier::beforeVfsChange);
  }

  private static void invokeAppliers(ChangeAppliers appliers, Consumer<AsyncFileListener.ChangeApplier> consumer) {
    if (!appliers.edtAppliers.isEmpty()) {
      VfsThreadingUtil.runActionOnEdtRegardlessOfCurrentThread(() -> {
        for (var applier : appliers.edtAppliers) {
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
      });
    }
    if (!appliers.backgroundAppliers.isEmpty()) {
      VfsThreadingUtil.runActionOnBackgroundRegardlessOfCurrentThread(() -> {
        for (var applier : appliers.backgroundAppliers) {
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
      });
    }
  }

  public static void afterVfsChange(@NotNull AsyncEventSupport.ChangeAppliers appliers) {
    invokeAppliers(appliers, AsyncFileListener.ChangeApplier::afterVfsChange);
  }

  @RequiresWriteLock
  static void processEventsFromRefresh(
    @NotNull List<CompoundVFileEvent> events,
    @NotNull AsyncEventSupport.ChangeAppliers appliers,
    boolean excludeAsyncListeners
  ) {
    beforeVfsChange(appliers);
    var split = appliers.split(applier -> applier instanceof AfterEventShouldBeFiredBeforeOtherListeners);
    var earlyAfterEventChangeAppliers = split.getFirst();
    var normalAfterEventChangeAppliers = split.getSecond();
    try {
      ((PersistentFSImpl)PersistentFS.getInstance()).processEventsImpl(events, earlyAfterEventChangeAppliers, excludeAsyncListeners);
    }
    catch (Throwable e) {
      rethrowControlFlowException(e);
      LOG.error(e);
    }
    finally {
      afterVfsChange(normalAfterEventChangeAppliers);
    }
  }
}
