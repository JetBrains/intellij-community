/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.Result;
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
  public void testDeletionEvents() {
    DummyFileSystem fs = new DummyFileSystem();

    Pair<VirtualFile, VirtualFile> pair = new WriteAction<Pair<VirtualFile, VirtualFile>>() {
      @Override
      protected void run(@NotNull Result<Pair<VirtualFile, VirtualFile>> result) throws IOException {
        VirtualFile root = fs.createRoot("root");
        VirtualFile file = root.createChildData(this, "f");
        result.setResult(pair(root, file));
      }
    }.execute().getResultObject();
    VirtualFile root = pair.first, file = pair.second;

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

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws IOException {
        file.delete(this);
      }
    }.execute();

    for (VirtualFileEvent event : events) {
      assertNotNull(event);
      assertEquals(file, event.getFile());
      assertEquals("f", event.getFileName());
      assertEquals(root, event.getParent());
    }
  }
}