package com.intellij.testFramework;

import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public abstract class FlyIdeaTestCase extends TestCase {

  private Disposable myRootDisposable;
  private File myTempDir;

  protected void setUp() throws Exception {
    MockApplication app = new MockApplication();
    myRootDisposable = Disposer.newDisposable();
    ApplicationManagerEx.setApplication(app, myRootDisposable);

  }

  public File getTempDir() throws IOException {
    if (myTempDir == null) {
      myTempDir = FileUtil.createTempDirectory(getName(), getClass().getName());
    }

    return myTempDir;
  }

  public Disposable getRootDisposable() {
    return myRootDisposable;
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    if (myTempDir != null) {
      FileUtil.asyncDelete(myTempDir);
    }
    Disposer.dispose(myRootDisposable);
  }
}
