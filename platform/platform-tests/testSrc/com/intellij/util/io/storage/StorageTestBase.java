// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.TemporaryDirectory;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.IOException;
import java.nio.file.Path;

public abstract class StorageTestBase {
  @Rule
  public final TemporaryDirectory tempDir = new TemporaryDirectory();
  private Path storagePath;

  protected Storage myStorage;

  @Before
  public void setUpStorage() throws IOException {
    if (storagePath == null) {
      storagePath = tempDir.newPath().resolve("test-storage");
    }
    myStorage = createStorage(storagePath);
  }

  @NotNull
  protected Storage createStorage(@NotNull Path fileName) throws IOException {
    return new Storage(fileName);
  }

  @After
  public void tearDown() {
    Disposer.dispose(myStorage);
  }
}