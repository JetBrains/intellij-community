// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@UsePMarkerImplementation
final class FileMarkerRootTest {
  @Test
  @Timeout(60)
  void attachmentWaitsForAFileRootUpdate() throws Exception {
    LightVirtualFile file = new LightVirtualFile("test.txt", "abcdef");
    //noinspection KotlinInternalInJava
    FileMarkerRoot fileRoot = FileMarkerRoot.Companion.getOrCreate$intellij_platform_core_impl(file);
    DocumentImpl document = new DocumentImpl("abcdef", true);
    CountDownLatch updateStarted = new CountDownLatch(1);
    CountDownLatch finishUpdate = new CountDownLatch(1);
    long markerId = 1L;

    //noinspection KotlinInternalInJava
    FutureTask<Boolean> updateTask = new FutureTask<>(() ->
      fileRoot.updateCurrentRoot$intellij_platform_core_impl(root -> {
        updateStarted.countDown();
        await(finishUpdate);
        return root.insert(markerId, 1, 2, new MarkerSpec(false, false), (byte)0, null, 0);
      })
    );
    new Thread(updateTask, "file marker root update").start();
    assertTrue(updateStarted.await(30, TimeUnit.SECONDS));

    FutureTask<Void> attachmentTask = new FutureTask<>(() -> {
      //noinspection KotlinInternalInJava
      FileMarkerRoot.restoreRangeMarkersFromFile(document, file, 4);
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
    assertTrue(document.getRangeMarkers().rootStore().containsMarkerId(document.getCore().snapshot(), markerId));
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
