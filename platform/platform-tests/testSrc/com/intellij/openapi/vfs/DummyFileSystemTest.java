// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;

import static com.intellij.openapi.util.Pair.pair;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DummyFileSystemTest extends BareTestFixtureTestCase {
  @Test
  public void testDeletionEvents() throws IOException {
    DummyFileSystem fs = new DummyFileSystem();

    Pair<VirtualFile, VirtualFile> pair = WriteAction.computeAndWait(() -> {
      VirtualFile root = fs.createRoot("root");
      VirtualFile file = root.createChildData(this, "f");
      return pair(root, file);
    });
    VirtualFile root = pair.first;
    VirtualFile file = pair.second;

    VirtualFileEvent[] events = new VirtualFileEvent[2];

    VirtualFileListener listener = new VirtualFileListener() {
      @Override
      public void beforeFileDeletion(@NotNull VirtualFileEvent e) {
        events[0] = e;
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent e) {
        events[1] = e;
      }
    };
    fs.addVirtualFileListener(listener);
    Disposer.register(getTestRootDisposable(), () -> fs.removeVirtualFileListener(listener));

    WriteAction.runAndWait(() -> file.delete(this));

    for (VirtualFileEvent event : events) {
      assertNotNull(event);
      assertEquals(file, event.getFile());
      assertEquals("f", event.getFileName());
      assertEquals(root, event.getParent());
    }
  }
}