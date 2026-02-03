// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VirtualFileListenerTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void testFireEvent() throws IOException {
    VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTempDir.newDirectory("vDir"));
    assertNotNull(dir);
    dir.getChildren();

    Ref<Boolean> eventFired = Ref.create(false);

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        eventFired.set(true);
      }
    }, getTestRootDisposable());

    WriteAction.computeAndWait(() -> dir.createChildData(this, "x.txt"));

    assertTrue(eventFired.get());
  }
}