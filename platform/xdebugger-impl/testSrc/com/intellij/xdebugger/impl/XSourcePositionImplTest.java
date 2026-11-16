// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XSourcePositionImplTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  @TempDir
  private Path tempDir;

  @Test
  public void testPositionByLine() throws Exception {
    VirtualFile file = createTestFile();
    checkLocksOrder(XSourcePositionImpl.create(file, 0), it -> it.getLine());
    checkLocksOrder(XSourcePositionImpl.create(file, 0), it -> it.getOffset());
  }

  @Test
  public void testPositionByOffset() throws Exception {
    VirtualFile file = createTestFile();
    XSourcePositionImpl position = XSourcePositionImpl.createByOffset(file, 0);
    checkLocksOrder(position, it -> it.getLine());
    checkLocksOrder(position, it -> it.getOffset());
  }

  @Test
  public void testPositionByElement() throws Exception {
    VirtualFile file = createTestFile();
    checkLocksOrder(createPositionByElement(file), it -> it.getLine());
    checkLocksOrder(createPositionByElement(file), it -> it.getOffset());
  }

  private static void checkLocksOrder(
    @NotNull XSourcePositionImpl position,
    @NotNull Consumer<XSourcePositionImpl> action
  ) throws Exception {
    // The check reproduces a deadlock involving two read threads computing
    // XSourcePositionImpl.getOffset() and a thread with write action.
    //
    // Deadlock occurred because it was possible to acquire locks in getOffset/getLine
    // in different order. On the example of getOffset for position created from line:
    //
    // Read thread 1           Write Action                Read thread 2
    // -----------------------------------------------------------------------
    // acquire read lock
    //                         try acquiring write lock
    //                         blocked by Read thread 1
    //                                                     call getOffset()
    //                                                     acquire NotNullLazyValue lock
    //                                                     try acquiring read lock
    //                                                     blocked by Write Action
    // call getOffset()
    // try acquiring NotNullLazyValue lock
    // blocked by Read thread 2

    Application app = ApplicationManager.getApplication();
    CountDownLatch beforeFirstCall = new CountDownLatch(1);
    CountDownLatch beforeSecondCall = new CountDownLatch(1);

    Thread t1 = new Thread("Read action 1") {
      @Override
      public void run() {
        app.runReadAction(() -> {
          try {
            beforeSecondCall.await();
            action.accept(position);
          }
          catch (InterruptedException e) {
            e.printStackTrace();
          }
        });
      }
    };
    t1.start();

    // let read action run so that write action blocks
    Thread.sleep(1000);

    app.invokeLater(() -> {
      app.runWriteIntentReadAction(() -> {
        app.runWriteAction(() -> {});
        return null;
      });
    });

    // let the write action block so that the second read action blocks as well
    Thread.sleep(1000);

    CountDownLatch firstCallDone = new CountDownLatch(1);
    Thread t2 = new Thread("Read action 2") {
      @Override
      public void run() {
        beforeFirstCall.countDown();
        action.accept(position);
        firstCallDone.countDown();
      }
    };
    t2.start();

    beforeFirstCall.await(5, TimeUnit.SECONDS);
    Thread.sleep(1000); // let the first call to start
    beforeSecondCall.countDown(); // allow the second call to run in the Read action 1

    boolean firstCallCompletes = firstCallDone.await(5, TimeUnit.SECONDS);
    if (!firstCallCompletes) {
      // Interrupt the write action so that the test doesn't deadlock.
      EDT.getEventDispatchThread().interrupt();
    }

    assertTrue(firstCallCompletes);
  }

  private @NotNull VirtualFile createTestFile() throws IOException {
    Path path = Files.writeString(tempDir.resolve("test.txt"), "some text");
    return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path));
  }

  private static @NotNull XSourcePositionImpl createPositionByElement(@NotNull VirtualFile file) {
    return ReadAction.compute(() -> {
      PsiFile psiFile = PsiManager.getInstance(projectFixture.get()).findFile(file);
      return XSourcePositionImpl.createByElement(psiFile.findElementAt(0));
    });
  }
}
