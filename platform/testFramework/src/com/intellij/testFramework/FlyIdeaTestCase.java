package com.intellij.testFramework;

import com.intellij.mock.MockApplicationEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

public abstract class FlyIdeaTestCase extends TestCase {

  private Disposable myRootDisposable;
  private File myTempDir;

  @Override
  protected void setUp() throws Exception {
    final Application old = ApplicationManagerEx.getApplication();
    myRootDisposable = Disposer.newDisposable();
    MockApplicationEx app = new MockApplicationEx(getRootDisposable()) {
      @NotNull
      @Override
      public Future<?> executeOnPooledThread(@NotNull Runnable action) {
        return old != null ? old.executeOnPooledThread(action) : super.executeOnPooledThread(action);
      }
    };
    ApplicationManager.setApplication(app, myRootDisposable);
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
    super.tearDown();
    if (myTempDir != null) {
      FileUtil.asyncDelete(myTempDir);
    }
    Disposer.dispose(myRootDisposable);
  }
}
