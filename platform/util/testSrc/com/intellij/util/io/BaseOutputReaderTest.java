// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BaseOutputReaderTest {
  private static final String[] TEST_DATA = Runner.TEST_DATA;

  private static class TestOutputReader extends BaseOutputReader {
    private final List<String> myLines = Collections.synchronizedList(new ArrayList<>());

    private TestOutputReader(InputStream stream, BaseOutputReader.Options options) {
      super(stream, null, options);
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
  public void testBlockingChunkRead() throws Exception {
    List<String> lines = readLines(BaseDataReader.SleepingPolicy.BLOCKING, false, true, true);
    assertThat(StringUtil.join(lines, "")).isEqualTo(StringUtil.join(Arrays.asList(TEST_DATA), ""));
  }

  @Test(timeout = 30000)
  public void testBlockingRead() throws Exception {
    List<String> lines = readLines(BaseDataReader.SleepingPolicy.BLOCKING, true, true, true);
    assertThat(lines.size()).isBetween(7, 8);
    assertThat(lines).startsWith(r(TEST_DATA[0]), r(TEST_DATA[1]), r(TEST_DATA[2])).endsWith(r(TEST_DATA[4]), r(TEST_DATA[5]), r(TEST_DATA[6]));
    assertThat(StringUtil.join(lines, "")).isEqualTo(StringUtil.join(TEST_DATA, BaseOutputReaderTest::r, ""));
  }

  @Test(timeout = 30000)
  public void testBlockingLineRead() throws Exception {
    List<String> lines = readLines(BaseDataReader.SleepingPolicy.BLOCKING, true, false, true);
    assertThat(lines).containsExactly(r(TEST_DATA[0]), r(TEST_DATA[1] + TEST_DATA[2]), r(TEST_DATA[3]), r(TEST_DATA[4] + TEST_DATA[5] + TEST_DATA[6]));
  }

  @Test(timeout = 30000)
  public void testBlockingLineReadTrimmed() throws Exception {
    List<String> lines = readLines(BaseDataReader.SleepingPolicy.BLOCKING, true, false, false);
    assertThat(lines).containsExactly(TEST_DATA[0].trim(), TEST_DATA[1] + TEST_DATA[2].trim(), TEST_DATA[3].trim(), TEST_DATA[4] + TEST_DATA[5] + TEST_DATA[6]);
  }

  @Test(timeout = 30000)
  public void testNonBlockingChunkRead() throws Exception {
    List<String> lines = readLines(BaseDataReader.SleepingPolicy.SIMPLE, false, true, true);
    assertThat(StringUtil.join(lines, "")).isEqualTo(StringUtil.join(Arrays.asList(TEST_DATA), ""));
  }

  @Test(timeout = 30000)
  public void testNonBlockingRead() throws Exception {
    List<String> lines = readLines(BaseDataReader.SleepingPolicy.SIMPLE, true, true, true);
    assertThat(lines.size()).as("chunks: " + lines).isBetween(7, 9);
    assertThat(lines).startsWith(r(TEST_DATA[0]), r(TEST_DATA[1]), r(TEST_DATA[2])).endsWith(r(TEST_DATA[4]), r(TEST_DATA[5]), r(TEST_DATA[6]));
    assertThat(StringUtil.join(lines, "")).isEqualTo(StringUtil.join(TEST_DATA, BaseOutputReaderTest::r, ""));
  }

  // Stopping is not supported for an open stream in blocking mode
  //@Test(timeout = 30000)
  //public void testBlockingStop() throws Exception {
  //  doStopTest(BaseDataReader.SleepingPolicy.BLOCKING);
  //}

  @Test(timeout = 30000)
  public void testNonBlockingStop() throws Exception {
    doStopTest(BaseDataReader.SleepingPolicy.SIMPLE);
  }

  private List<String> readLines(BaseDataReader.SleepingPolicy policy, boolean split, boolean incomplete, boolean separators) throws Exception {
    Process process = launchTest("data");
    TestOutputReader reader = new TestOutputReader(process.getInputStream(), new BaseOutputReader.Options() {
      @Override public BaseDataReader.SleepingPolicy policy() { return policy; }
      @Override public boolean splitToLines() { return split; }
      @Override public boolean sendIncompleteLines() { return incomplete; }
      @Override public boolean withSeparators() { return separators; }
    });

    process.waitFor();
    reader.stop();
    reader.waitFor();

    assertEquals(0, process.exitValue());

    return reader.myLines;
  }

  private void doStopTest(@SuppressWarnings("SameParameterValue") BaseDataReader.SleepingPolicy policy) throws Exception {
    Process process = launchTest("sleep");
    TestOutputReader reader = new TestOutputReader(process.getInputStream(), BaseOutputReader.Options.withPolicy(policy));

    try {
      reader.stop();
      reader.waitFor();
    }
    finally {
      process.destroy();
      process.waitFor();
    }
  }

  private static String r(String line) {
    return StringUtil.endsWith(line, "\r\n") ? line.substring(0, line.length() - 2) + '\n' : line;
  }

  private Process launchTest(String mode) throws Exception {
    String java = System.getProperty("java.home") + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java");

    String className = BaseOutputReaderTest.Runner.class.getName();
    URL url = getClass().getClassLoader().getResource(className.replace('.', '/') + ".class");
    assertNotNull(url);
    File dir = new File(url.toURI());
    for (int i = 0; i < StringUtil.countChars(className, '.') + 1; i++) dir = dir.getParentFile();

    String[] cmd = {java, "-cp", dir.getPath(), className, mode};
    return new ProcessBuilder(cmd).redirectErrorStream(true).start();
  }

  public static class Runner {
    private static final String[] TEST_DATA = {
      "first\r\n",
      "incomplete",
      "-continuation\r\n",
      new String(new char[8*1024-1]).replace('\0', 'x') + "\r\n",
      "last",
      "\rdone.",
      "\r"
    };

    private static final int SEND_TIMEOUT = 500;
    private static final int SLEEP_TIMEOUT = 60000;

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
}