// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public abstract class FlyIdeaTestCase extends TestCase {
  private final Disposable myRootDisposable = Disposer.newDisposable();
  private File myTempDir;

  @Override
  protected void setUp() throws Exception {
    TestApplicationManager.getInstance();
  }

  public File getTempDir() throws IOException {
    if (myTempDir == null) {
      myTempDir = FileUtil.createTempDirectory(getName(), getClass().getName(),false);
    }

    return myTempDir;
  }

  public Disposable getRootDisposable() {
    return myRootDisposable;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myTempDir != null) {
        FileUtil.asyncDelete(myTempDir);
      }
      Disposer.dispose(myRootDisposable);
    }
    finally {
      super.tearDown();
    }
  }
}
