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
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BaseOutputReaderTest {
  private static final String[] TEST_DATA = {"first\n", "incomplete", "-continuation\n", "last\n"};
  private static final int SEND_TIMEOUT = 500;
  private static final int SLEEP_TIMEOUT = 60000;

  private static class TestOutputReader extends BaseOutputReader {
    private final List<String> myLines = Collections.synchronizedList(new ArrayList<String>());

    private TestOutputReader(InputStream stream, SleepingPolicy sleepingPolicy) {
      super(stream, null, sleepingPolicy);
      start(BaseOutputReaderTest.class.getSimpleName());
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      myLines.add(text);
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return AppExecutorUtil.getAppExecutorService().submit(runnable);
    }
  }

  @Test(timeout = 30000)
  public void testBlockingRead() throws Exception {
    doReadTest(BaseDataReader.SleepingPolicy.BLOCKING);
  }

  @Test(timeout = 30000)
  public void testNonBlockingRead() throws Exception {
    doReadTest(BaseDataReader.SleepingPolicy.SIMPLE);
  }

  @Test(timeout = 30000)
  public void testBlockingStop() throws Exception {
    doStopTest(BaseDataReader.SleepingPolicy.BLOCKING);
  }

  @Test(timeout = 30000)
  public void testNonBlockingStop() throws Exception {
    doStopTest(BaseDataReader.SleepingPolicy.SIMPLE);
  }

  private void doReadTest(BaseDataReader.SleepingPolicy policy) throws Exception {
    Process process = launchTest("data");
    TestOutputReader reader = new TestOutputReader(process.getInputStream(), policy);

    process.waitFor();
    reader.stop();
    reader.waitFor();

    assertEquals(0, process.exitValue());
    assertEquals(Arrays.asList(TEST_DATA), reader.myLines);
  }

  private void doStopTest(BaseDataReader.SleepingPolicy policy) throws Exception {
    Process process = launchTest("sleep");
    TestOutputReader reader = new TestOutputReader(process.getInputStream(), policy);

    try {
      reader.stop();
      reader.waitFor();
    }
    finally {
      process.destroy();
      process.waitFor();
    }
  }

  private Process launchTest(String mode) throws Exception {
    String java = System.getProperty("java.home") + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java");

    String className = BaseOutputReaderTest.class.getName();
    URL url = getClass().getClassLoader().getResource(className.replace('.', '/') + ".class");
    assertNotNull(url);
    File dir = new File(url.toURI());
    for (int i = 0; i < StringUtil.countChars(className, '.') + 1; i++) dir = dir.getParentFile();

    String[] cmd = {java, "-cp", dir.getPath(), className, mode};
    return new ProcessBuilder(cmd).redirectErrorStream(true).start();
  }

  @SuppressWarnings("BusyWait")
  public static void main(String[] args) throws InterruptedException {
    if (args.length > 0 && "sleep".equals(args[0])) {
      Thread.sleep(SLEEP_TIMEOUT);
    }
    else if (args.length > 0 && "data".equals(args[0])) {
      for (String line : TEST_DATA) {
        System.out.print(line);
        Thread.sleep(SEND_TIMEOUT);
      }
    }
    else {
      throw new IllegalArgumentException(Arrays.toString(args));
    }
  }
}