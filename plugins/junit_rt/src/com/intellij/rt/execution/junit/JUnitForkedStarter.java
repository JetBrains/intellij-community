/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 * @since 6.04.2011
 */
public class JUnitForkedStarter {
  private JUnitForkedStarter() { }

  public static void main(String[] args) throws Exception {
    final String testOutputPath = args[0];
    final int lastIdx = Integer.parseInt(args[1]);
    final boolean isJUnit4 = args[2].equalsIgnoreCase("true");
    final String[] childTestDescription = {args[3]};
    final ArrayList listeners = new ArrayList();
    for (int i = 4, argsLength = args.length; i < argsLength; i++) {
      listeners.add(args[i]);
    }

    final File file = new File(testOutputPath);
    if (!file.exists()) {
      if (!file.createNewFile()) return;
    }
    final FileOutputStream stream = new FileOutputStream(testOutputPath);
    //noinspection UseOfSystemOutOrSystemErr
    PrintStream oldOut = System.out;
    //noinspection UseOfSystemOutOrSystemErr
    PrintStream oldErr = System.err;
    try {
      final PrintStream out = new PrintStream(new ForkedVMWrapper(stream, false));
      final PrintStream err = new PrintStream(new ForkedVMWrapper(stream, true));
      System.setOut(out);
      System.setErr(err);
      IdeaTestRunner testRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(isJUnit4).newInstance();
      //noinspection IOResourceOpenedButNotSafelyClosed
      testRunner.setStreams(new SegmentedOutputStream(out, true), new SegmentedOutputStream(err, true), lastIdx);
      System.exit(testRunner.startRunnerWithArgs(childTestDescription, listeners, false));
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      stream.close();
    }
  }

  static int startForkedVMs(String[] args,
                            boolean isJUnit4,
                            List listeners,
                            SegmentedOutputStream out,
                            SegmentedOutputStream err,
                            String forkMode,
                            String path) throws Exception {
    final List parameters = new ArrayList();
    final BufferedReader bufferedReader = new BufferedReader(new FileReader(path));
    try {
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        parameters.add(line);
      }
    }
    finally {
      bufferedReader.close();
    }

    IdeaTestRunner testRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(isJUnit4).newInstance();
    testRunner.setStreams(out, err, 0);
    final Object description = testRunner.getTestToStart(args);
    if (description == null) return -1;

    TreeSender.sendTree(testRunner, description);

    long time = System.currentTimeMillis();

    final List children = testRunner.getChildTests(description);
    final boolean forkTillMethod = forkMode.equalsIgnoreCase("method");
    int result = processChildren(isJUnit4, listeners, out, err, parameters, testRunner, children, 0, forkTillMethod);

    time = System.currentTimeMillis() - time;
    new TimeSender(testRunner.getRegistry()).printHeader(time);
    return result;
  }

  private static int processChildren(boolean isJUnit4,
                                     List listeners,
                                     SegmentedOutputStream out,
                                     SegmentedOutputStream err,
                                     List parameters,
                                     IdeaTestRunner testRunner,
                                     List children,
                                     int result,
                                     boolean forkTillMethod) throws IOException, InterruptedException {
    for (int i = 0, argsLength = children.size(); i < argsLength; i++) {
      final Object child = children.get(i);
      final List childTests = testRunner.getChildTests(child);
      final int childResult = childTests.isEmpty() || !forkTillMethod
                              ? runChild(child, isJUnit4, listeners, out, err, parameters, testRunner, forkTillMethod)
                              : processChildren(isJUnit4, listeners, out, err, parameters, testRunner, childTests, result, forkTillMethod);
      result = Math.min(childResult, result);
    }
    return result;
  }

  private static int runChild(Object child,
                              boolean isJUnit4,
                              List listeners,
                              SegmentedOutputStream out,
                              SegmentedOutputStream err,
                              List parameters,
                              IdeaTestRunner testRunner,
                              boolean forkTillMethod) throws IOException, InterruptedException {
    //noinspection SSBasedInspection
    final File tempFile = File.createTempFile("fork", "test");
    final String testOutputPath = tempFile.getAbsolutePath();
    final int knownObject = testRunner.getRegistry().getKnownObject(child);

    final ProcessBuilder builder = new ProcessBuilder();
    builder.add(parameters);
    builder.add(JUnitForkedStarter.class.getName());
    builder.add(testOutputPath);
    builder.add(String.valueOf(knownObject + (forkTillMethod ? 0 : 1)));
    builder.add(String.valueOf(isJUnit4));
    builder.add(testRunner.getStartDescription(child));
    builder.add(listeners);

    final Process exec = builder.createProcess();
    final int result = exec.waitFor();
    ForkedVMWrapper.readWrapped(testOutputPath, out.getPrintStream(), err.getPrintStream());
    return result;
  }
}
