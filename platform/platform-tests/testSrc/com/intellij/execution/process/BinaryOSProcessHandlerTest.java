// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BinaryOSProcessHandlerTest {
  @Test public void testBlocking() { doTest(true); }
  @Test public void testNonBlocking() { doTest(false); }

  private static void doTest(boolean blocking) {
    try {
      TestBinaryOSProcessHandler handler = new TestBinaryOSProcessHandler(launchTest(), blocking);
      handler.startNotify();
      assertTrue(handler.waitFor(60 * 1000));
      assertEquals(0, handler.exitCode);
      assertThat(handler.stdErr.toString()).isEqualTo(Runner.TEXT);
      assertThat(handler.getOutput()).containsExactly(Runner.BYTES);
    }
    catch (URISyntaxException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Process launchTest() throws URISyntaxException, IOException {
    Class<Runner> runnerClass = Runner.class;
    String classPath = PathManager.getJarPathForClass(runnerClass);
    String[] cmd = {PlatformTestUtil.getJavaExe(), "-cp", classPath, runnerClass.getName()};
    return new ProcessBuilder(cmd).redirectErrorStream(false).start();
  }

  private static class TestBinaryOSProcessHandler extends BinaryOSProcessHandler {
    private final StringBuilder stdErr = new StringBuilder();
    private final boolean blocking;
    private int exitCode = -1;

    TestBinaryOSProcessHandler(Process process, boolean blocking) {
      super(process, "test", null);
      this.blocking = blocking;
    }

    @NotNull
    @Override
    protected BaseOutputReader.Options readerOptions() {
      return blocking ? BaseOutputReader.Options.BLOCKING : BaseOutputReader.Options.NON_BLOCKING;
    }

    @Override
    public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
      if (outputType != ProcessOutputTypes.SYSTEM) {
        stdErr.append(text);
      }
    }

    @Override
    protected void notifyProcessTerminated(int exitCode) {
      super.notifyProcessTerminated(exitCode);
      this.exitCode = exitCode;
    }
  }

  public static final class Runner {
    private static final String TEXT = "some\ntext";
    private static final byte[] BYTES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final int PACKET_SIZE = 4;
    private static final int SEND_TIMEOUT = 500;

    @SuppressWarnings("BusyWait")
    public static void main(String[] args) throws InterruptedException {
      System.err.print(TEXT);

      for (int offset = 0; offset < BYTES.length; offset += PACKET_SIZE) {
        int n = Math.min(PACKET_SIZE, BYTES.length - offset);
        System.out.write(BYTES, offset, n);
        Thread.sleep(SEND_TIMEOUT);
      }
    }
  }
}