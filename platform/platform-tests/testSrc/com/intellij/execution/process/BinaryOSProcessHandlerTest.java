/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

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
    String className = Runner.class.getName();
    URL url = Runner.class.getClassLoader().getResource(className.replace('.', '/') + ".class");
    assertNotNull(url);
    File dir = new File(url.toURI());
    for (int i = 0; i < StringUtil.countChars(className, '.') + 1; i++) dir = dir.getParentFile();

    String[] cmd = {PlatformTestUtil.getJavaExe(), "-cp", dir.getPath(), className};
    return new ProcessBuilder(cmd).redirectErrorStream(false).start();
  }

  private static class TestBinaryOSProcessHandler extends BinaryOSProcessHandler {
    private final StringBuilder stdErr = new StringBuilder();
    private final boolean blocking;
    private int exitCode = -1;

    public TestBinaryOSProcessHandler(Process process, boolean blocking) {
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

  public static class Runner {
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