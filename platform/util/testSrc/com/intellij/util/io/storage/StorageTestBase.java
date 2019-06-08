// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.File;
import java.io.IOException;

public abstract class StorageTestBase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  protected Storage myStorage;

  @Before
  public void setUpStorage() throws IOException {
    myStorage = createStorage(new File(tempDir.getRoot(), "test-storage").getPath());
  }

  @NotNull
  protected Storage createStorage(@NotNull String fileName) throws IOException {
    return new Storage(fileName);
  }

  @After
  public void tearDown() {
    Disposer.dispose(myStorage);
  }
}