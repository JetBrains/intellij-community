// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentPatch;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@UsePMarkerImplementation
final class VirtualFileMarkerRootTest {
  @Test
  void documentFullTraversalIncludesFileMarkersBeyondTheDocumentLength() {
    var file = new LightVirtualFile("test.txt", "abcdef");
    var fileMarker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, 10, -1, -1, -1, -1, false);
    var document = new DocumentImpl("abcdef", true);
    VirtualFileMarkerRoot.restoreRangeMarkersFromFile(document, file, 4);
    var documentMarker = document.createRangeMarker(1, 2);
    var visited = new ArrayList<RangeMarker>();

    assertTrue(document.processRangeMarkers(marker -> {
      visited.add(marker);
      return true;
    }));

    assertEquals(List.of(documentMarker, fileMarker), visited);
  }

  @Test
  void restorationComparesResolvedMarkerOffsetsInsteadOfJustMarkerEntryNodeStart() {
    var file = new LightVirtualFile("test.txt", "abcdef");
    var firstMarker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, 1, 0, 1, 0, 1, true);
    var middleMarker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, 3, 0, 3, 0, 3, true);
    var lastMarker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, 5, 0, 5, 0, 5, true);
    //noinspection KotlinInternalInJava
    var fileRoot = VirtualFileMarkerRoot.Companion.getOrCreate$intellij_platform_core_impl(file);
    var document = new DocumentImpl("abcdef", true);
    var beforeText = document.getCore().snapshot().text();
    var patch = DocumentPatch.simple(0, 0, "xx", 1, false);
    var afterText = beforeText.replace(patch.startOffset(), patch.endOffset(), patch.newFragment());

    fileRoot.updateCurrentRoot(root ->
      root.applyPatch(patch, beforeText, afterText, PMarkerRoot.EMPTY_HANDLE_CONSUMER, PMarkerRoot.EMPTY_HANDLE_CONSUMER)
    );

    VirtualFileMarkerRoot.restoreRangeMarkersFromFile(document, file, 4);

    assertEquals(1, firstMarker.getStartOffset());
    assertEquals(3, middleMarker.getStartOffset());
    assertEquals(5, lastMarker.getStartOffset());
  }

  @ParameterizedTest
  @ValueSource(ints = {3, 5})
  void restorationResolvesUnchangedMarkersBeforeLaterRestores(int initialOffset) {
    var file = new LightVirtualFile("test.txt", "abcdef");
    var unchangedMarker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, 1, 0, 1, 0, 1, true);
    var restoredMarker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, initialOffset, 0, 3, 0, 3, true);
    var offsetMarker = SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, 5, -1, -1, -1, -1, false);
    var document = new DocumentImpl("abcdef", true);

    VirtualFileMarkerRoot.restoreRangeMarkersFromFile(document, file, 4);

    assertEquals(1, unchangedMarker.getStartOffset());
    assertEquals(3, restoredMarker.getStartOffset());
    assertEquals(5, offsetMarker.getStartOffset());

    document.insertString(0, "x");
    VirtualFileMarkerRoot.restoreRangeMarkersFromFile(document, file, 4);

    assertEquals(2, unchangedMarker.getStartOffset());
    assertEquals(4, restoredMarker.getStartOffset());
    assertEquals(6, offsetMarker.getStartOffset());
  }

  @Test
  @Timeout(60)
  void attachmentWaitsForAFileRootUpdate() throws Exception {
    LightVirtualFile file = new LightVirtualFile("test.txt", "abcdef");
    //noinspection KotlinInternalInJava
    VirtualFileMarkerRoot fileRoot = VirtualFileMarkerRoot.Companion.getOrCreate$intellij_platform_core_impl(file);
    DocumentImpl document = new DocumentImpl("abcdef", true);
    CountDownLatch updateStarted = new CountDownLatch(1);
    CountDownLatch finishUpdate = new CountDownLatch(1);
    var marker = (SnapshotRangeMarkerImpl)SnapshotMarkerEngineImpl.INSTANCE.createRangeMarkerForVirtualFile(file, 0, -1, -1, -1, -1, false);
    var handle = fileRoot.currentRootReference$intellij_platform_core_impl().get().createHandle(marker, false);

    FutureTask<Boolean> updateTask = new FutureTask<>(() ->
      fileRoot.updateCurrentRoot(root -> {
        updateStarted.countDown();
        await(finishUpdate);
        return root.insert(handle, 1, 2, MarkerSpec.create(false, false), (byte)0, 0, DefaultMarkerPolicy.INSTANCE);
      })
    );
    new Thread(updateTask, "file marker root update").start();
    assertTrue(updateStarted.await(30, TimeUnit.SECONDS));

    FutureTask<Void> attachmentTask = new FutureTask<>(() -> {
      VirtualFileMarkerRoot.restoreRangeMarkersFromFile(document, file, 4);
      return null;
    });
    Thread attachmentThread = new Thread(attachmentTask, "file marker root attachment");
    attachmentThread.start();

    try {
      waitUntilBlockedOrCompleted(attachmentThread, attachmentTask);
      assertEquals(Thread.State.BLOCKED, attachmentThread.getState());
    }
    finally {
      finishUpdate.countDown();
    }

    assertTrue(updateTask.get(10, TimeUnit.SECONDS));
    attachmentTask.get(10, TimeUnit.SECONDS);
    assertTrue(document.getRangeMarkers().rootStore().containsMarker(document.getCore().snapshot(), handle));
  }

  private static void waitUntilBlockedOrCompleted(Thread thread, Future<?> completion) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (thread.getState() != Thread.State.BLOCKED && !completion.isDone() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
