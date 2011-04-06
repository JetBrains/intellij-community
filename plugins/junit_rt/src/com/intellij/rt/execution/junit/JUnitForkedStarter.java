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
import java.util.Iterator;
import java.util.List;

/**
* User: anna
* Date: 4/6/11
*/
public class JUnitForkedStarter {
  private JUnitForkedStarter() {
  }

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
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try {
      final PrintStream out = new PrintStream(new ForkedVMWrapper(stream, false));
      final PrintStream err = new PrintStream(new ForkedVMWrapper(stream, true));
      System.setOut(out);
      System.setErr(err);
      IdeaTestRunner testRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(isJUnit4).newInstance();
      testRunner.setStreams(new SegmentedOutputStream(out, true), new SegmentedOutputStream(err, true), lastIdx);
      System.exit(testRunner.startRunnerWithArgs(childTestDescription, listeners, false));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      stream.close();
    }
  }

  static int startForkedVMs(String[] args,
                            boolean isJUnit4,
                            ArrayList listeners,
                            SegmentedOutputStream out,
                            SegmentedOutputStream err, String commandline) throws Exception {
    IdeaTestRunner testRunner = (IdeaTestRunner)JUnitStarter.getAgentClass(isJUnit4).newInstance();
    testRunner.setStreams(out, err, 0);
    final Object description = testRunner.getTestToStart(args);

    TreeSender.sendTree(testRunner, description);

    long startTime = System.currentTimeMillis();

    final List children = testRunner.getChildTests(description);
    final boolean forkTillMethod = System.getProperty("idea.fork.junit.tests").equalsIgnoreCase("method");
    int result = processChildren(isJUnit4, listeners, out, err, commandline, testRunner, children, 0, forkTillMethod);

    long endTime = System.currentTimeMillis();
    long runTime = endTime - startTime;
    new TimeSender(testRunner.getRegistry()).printHeader(runTime);
    return result;
  }

  private static int processChildren(boolean isJUnit4,
                                     ArrayList listeners,
                                     SegmentedOutputStream out,
                                     SegmentedOutputStream err,
                                     String commandline, IdeaTestRunner testRunner, List children, int result, boolean forkTillMethod)
    throws IOException, InterruptedException {
    for (int i = 0, argsLength = children.size(); i < argsLength; i++) {
      final Object child = children.get(i);
      final List childTests = testRunner.getChildTests(child);
      if (childTests.isEmpty() || !forkTillMethod) {
        result = Math.min(runChild(child, isJUnit4, listeners, out, err, commandline, testRunner, forkTillMethod), result);
      } else {
        result = Math.min(processChildren(isJUnit4, listeners, out, err, commandline, testRunner, childTests, result, forkTillMethod), result);
      }
    }
    return result;
  }

  private static int runChild(Object child,
                              boolean isJUnit4,
                              ArrayList listeners,
                              SegmentedOutputStream out,
                              SegmentedOutputStream err,
                              String commandline, IdeaTestRunner testRunner,
                              boolean forkTillMethod)
    throws IOException, InterruptedException {
    final File tempFile = File.createTempFile("fork", "test");
    final String testOutputPath = tempFile.getAbsolutePath();
    final int knownObject = testRunner.getRegistry().getKnownObject(child);
    String command = commandline + " " + JUnitForkedStarter.class.getName()+ " " + testOutputPath + " " + (knownObject + (forkTillMethod ? 0 : 1)) + " " +
                      isJUnit4 + " " + testRunner.getStartDescription(child) ;
    for (Iterator iterator = listeners.iterator(); iterator.hasNext(); ) {
      command += " " + iterator.next();
    }
    final Process exec = Runtime.getRuntime().exec(command);
    int result = exec.waitFor();
    ForkedVMWrapper.readWrapped(testOutputPath, out.getPrintStream(), err.getPrintStream());
   // tempFile.deleteOnExit();
    return result;
  }

  static String getForkedCommandLine() throws IOException {
    final String path = System.getProperty("idea.fork.junit.tests") != null ? System.getProperty("idea.command.line") : null;
    String commandline = null;
    if (path != null) {
      final BufferedReader reader = new BufferedReader(new FileReader(path));
      commandline = reader.readLine();
      reader.close();
    }
    return commandline;
  }
}
