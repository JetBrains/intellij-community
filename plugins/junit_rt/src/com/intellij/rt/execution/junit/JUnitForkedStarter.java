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
 * @author anna
 * @since 6.04.2011
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

  static int startForkedVMs(String workingDirsPath,
                            String[] args,
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

    TreeSender.sendTree(testRunner, description, true);

    long time = System.currentTimeMillis();

    int result = 0;
    if (workingDirsPath == null || new File(workingDirsPath).length() == 0) {
       final List children = testRunner.getChildTests(description);
       final boolean forkTillMethod = forkMode.equalsIgnoreCase("method");
       result = processChildren(isJUnit4, listeners, out, err, parameters, testRunner, children, 0, forkTillMethod, null);
    } else {
      final BufferedReader perDirReader = new BufferedReader(new FileReader(workingDirsPath));
      try {
        final String packageName = perDirReader.readLine();
        String workingDir;
        while ((workingDir = perDirReader.readLine()) != null) {
          try {
            File tempFile = File.createTempFile("idea_junit", ".tmp");
            tempFile.deleteOnExit();

            final FileOutputStream writer = new FileOutputStream(tempFile);

            List classNames = new ArrayList();
            try {
              final int classNamesSize = Integer.parseInt(perDirReader.readLine());
              writer.write((packageName + ", working directory: \'" + workingDir + "\'\n").getBytes("UTF-8")); //instead of package name
              for (int i = 0; i < classNamesSize; i++) {
                String className = perDirReader.readLine();
                if (className == null) {
                  System.err.println("Class name is expected. Working dir: " + workingDir);
                  return -1;
                }
                classNames.add(className);
                writer.write((className + "\n").getBytes("UTF-8"));
              }
            }
            finally {
              writer.close();
            }

            final Object rootDescriptor = findByClassName(testRunner, (String)classNames.get(0), description);
            final int childResult;
            final File dir = new File(workingDir);
            if (forkMode.equals("none")) {
              childResult =
                runChild(isJUnit4, listeners, out, err, parameters, "@" + tempFile.getAbsolutePath(), dir,
                         String.valueOf(testRunner.getRegistry().getKnownObject(rootDescriptor) - 1));
            } else {
              final List children = new ArrayList(testRunner.getChildTests(description));
              for (Iterator iterator = children.iterator(); iterator.hasNext(); ) {
                if (!classNames.contains(testRunner.getTestClassName(iterator.next()))) {
                  iterator.remove();
                }
              }
              final boolean forkTillMethod = forkMode.equalsIgnoreCase("method");
              childResult = processChildren(isJUnit4, listeners, out, err, parameters, testRunner, children, result, forkTillMethod, dir);
            }
            result = Math.min(childResult, result);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
      finally {
        perDirReader.close();
      }
    }

    time = System.currentTimeMillis() - time;
    new TimeSender(testRunner.getRegistry()).printHeader(time);
    return result;
  }

  private static Object findByClassName(IdeaTestRunner testRunner, String className, Object rootDescription) {
    final List children = testRunner.getChildTests(rootDescription);
    for (int i = 0; i < children.size(); i++) {
      Object child = children.get(i);
      if (className.equals(testRunner.getTestClassName(child))) {
        return child;
      }
    }
    for (int i = 0; i < children.size(); i++) {
      final Object byName = findByClassName(testRunner, className, children.get(i));
      if (byName != null) return byName;
    }
    return null;
  }
  
  private static int processChildren(boolean isJUnit4,
                                     List listeners,
                                     SegmentedOutputStream out,
                                     SegmentedOutputStream err,
                                     List parameters,
                                     IdeaTestRunner testRunner,
                                     List children,
                                     int result,
                                     boolean forkTillMethod, File workingDir) throws IOException, InterruptedException {
    for (int i = 0, argsLength = children.size(); i < argsLength; i++) {
      final Object child = children.get(i);
      final List childTests = testRunner.getChildTests(child);
      final int childResult;
      if (childTests.isEmpty() || !forkTillMethod) {
        final int startIndex = testRunner.getRegistry().getKnownObject(child);
        childResult =
          runChild(isJUnit4, listeners, out, err, parameters, testRunner.getStartDescription(child), workingDir,
                   String.valueOf(startIndex));
      }
      else {
        childResult =
          processChildren(isJUnit4, listeners, out, err, parameters, testRunner, childTests, result, forkTillMethod, workingDir);
      }
      result = Math.min(childResult, result);
    }
    return result;
  }

  private static int runChild(boolean isJUnit4,
                              List listeners,
                              SegmentedOutputStream out,
                              SegmentedOutputStream err,
                              List parameters,
                              String description,
                              File workingDir, 
                              String startIndex) throws IOException, InterruptedException {
    //noinspection SSBasedInspection
    final File tempFile = File.createTempFile("fork", "test");
    final String testOutputPath = tempFile.getAbsolutePath();

    final ProcessBuilder builder = new ProcessBuilder();
    builder.add(parameters);
    builder.add(JUnitForkedStarter.class.getName());
    builder.add(testOutputPath);
    builder.add(startIndex);
    builder.add(String.valueOf(isJUnit4));
    builder.add(description);
    builder.add(listeners);
    builder.setWorkingDir(workingDir);

    final Process exec = builder.createProcess();
    final int result = exec.waitFor();
    ForkedVMWrapper.readWrapped(testOutputPath, out.getPrintStream(), err.getPrintStream());
    return result;
  }
}
