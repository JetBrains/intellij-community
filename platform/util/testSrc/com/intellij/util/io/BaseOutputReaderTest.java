/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BaseOutputReaderTest {
  private static final String[] TEST_DATA = {"first\n", "incomplete", "-continuation\n", "last\n"};
  private static final int TIMEOUT = 100;

  private static ThreadPoolExecutor ourExecutor;

  private static class TestOutputReader extends BaseOutputReader {
    private final List<String> myLines = Collections.synchronizedList(new ArrayList<String>());

    public TestOutputReader(InputStream stream, SleepingPolicy sleepingPolicy) {
      super(stream, null, sleepingPolicy);
      start();
    }

    @Override
    protected void onTextAvailable(@NotNull String text) {
      myLines.add(text);
    }

    @Override
    protected Future<?> executeOnPooledThread(Runnable runnable) {
      return ourExecutor.submit(runnable);
    }
  }

  @BeforeClass
  public static void setUp() {
    ourExecutor = ConcurrencyUtil.newSingleThreadExecutor(BaseOutputReaderTest.class.getName());
  }

  @AfterClass
  public static void tearDown() {
    ourExecutor.shutdown();
    ourExecutor = null;
  }

  @Test(timeout = 30000)
  public void testBlocking() throws Exception {
    doTest(BaseDataReader.SleepingPolicy.BLOCKING);
  }

  @Test(timeout = 30000)
  public void testNonBlocking() throws Exception {
    doTest(BaseDataReader.SleepingPolicy.SIMPLE);
  }

  private void doTest(BaseDataReader.SleepingPolicy policy) throws IOException, URISyntaxException, InterruptedException {
    String java = System.getProperty("java.home") + (SystemInfo.isWindows ? "\\bin\\java.exe" : "/bin/java");

    String className = BaseOutputReaderTest.class.getName();
    URL url = getClass().getClassLoader().getResource(className.replace(".", "/") + ".class");
    assertNotNull(url);
    File dir = new File(url.toURI());
    for (int i = 0; i < StringUtil.countChars(className, '.') + 1; i++) dir = dir.getParentFile();

    Process process = new ProcessBuilder(java, "-cp", dir.getPath(), className).redirectErrorStream(true).start();
    TestOutputReader reader = new TestOutputReader(process.getInputStream(), policy);
    process.waitFor();
    reader.stop();
    reader.waitFor();

    assertEquals(0, process.exitValue());
    assertEquals(Arrays.asList(TEST_DATA), reader.myLines);
  }

  @SuppressWarnings("BusyWait")
  public static void main(String[] args) throws InterruptedException {
    for (String line : TEST_DATA) {
      System.out.print(line);
      Thread.sleep(TIMEOUT);
    }
  }
}
