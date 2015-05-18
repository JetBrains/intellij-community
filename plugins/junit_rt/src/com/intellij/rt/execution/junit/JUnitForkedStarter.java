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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.CommandLineWrapper;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author anna
 * @since 6.04.2011
 */
public class JUnitForkedStarter {

  public static final String DEBUG_SOCKET = "-debugSocket";

  private int myDebugPort = -1;
  private Socket myDebugSocket;

  JUnitForkedStarter() {
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
      System.exit(testRunner.startRunnerWithArgs(childTestDescription, listeners, null, 1, false));
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      stream.close();
    }
  }

  int startForkedVMs(String workingDirsPath,
                            String[] args,
                            boolean isJUnit4,
                            List listeners,
                            String params, Object out,
                            Object err,
                            String forkMode,
                            String path) throws Exception {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith(DEBUG_SOCKET)) {
        final List list = new ArrayList(Arrays.asList(args));
        list.remove(arg);
        args = (String[])list.toArray(new String[list.size()]);
        myDebugPort = Integer.parseInt(arg.substring(DEBUG_SOCKET.length()));
        break;
      }
    }

    final List parameters = new ArrayList();
    final BufferedReader bufferedReader = new BufferedReader(new FileReader(path));
    final String dynamicClasspath = bufferedReader.readLine();
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
    final Object description = testRunner.getTestToStart(args, params);
    if (description == null) return -1;

    TreeSender.sendTree(testRunner, description, !JUnitStarter.SM_RUNNER);

    long time = System.currentTimeMillis();

    int result = 0;
    if (workingDirsPath == null || new File(workingDirsPath).length() == 0) {
       final List children = testRunner.getChildTests(description);
       final boolean forkTillMethod = forkMode.equalsIgnoreCase("method");
       result = processChildren(isJUnit4, listeners, out, err, parameters, testRunner, children, 0, forkTillMethod, null, System.getProperty("java.class.path"), dynamicClasspath);
    } else {
      final BufferedReader perDirReader = new BufferedReader(new FileReader(workingDirsPath));
      try {
        final String packageName = perDirReader.readLine();
        String workingDir;
        while ((workingDir = perDirReader.readLine()) != null) {
          final String classpath = perDirReader.readLine();
          try {

            List classNames = new ArrayList();
            final int classNamesSize = Integer.parseInt(perDirReader.readLine());
            for (int i = 0; i < classNamesSize; i++) {
              String className = perDirReader.readLine();
              if (className == null) {
                System.err.println("Class name is expected. Working dir: " + workingDir);
                return -1;
              }
              classNames.add(className);
            }

            final Object rootDescriptor = findByClassName(testRunner, (String)classNames.get(0), description);
            final int childResult;
            final File dir = new File(workingDir);
            if (forkMode.equals("none")) {
              File tempFile = File.createTempFile("idea_junit", ".tmp");
              tempFile.deleteOnExit();
              JUnitStarter.printClassesList(classNames, packageName + ", working directory: \'" + workingDir + "\'", "", tempFile);
              final OutputObjectRegistry registry = testRunner.getRegistry();
              final String startIndex = String.valueOf(registry != null ? registry.getKnownObject(rootDescriptor) - 1 : -1);
              childResult =
                runChild(isJUnit4, listeners, out, err, parameters, "@" + tempFile.getAbsolutePath(), dir, startIndex, classpath, dynamicClasspath);
            } else {
              final List children = new ArrayList(testRunner.getChildTests(description));
              for (Iterator iterator = children.iterator(); iterator.hasNext(); ) {
                if (!classNames.contains(testRunner.getTestClassName(iterator.next()))) {
                  iterator.remove();
                }
              }
              final boolean forkTillMethod = forkMode.equalsIgnoreCase("method");
              childResult = processChildren(isJUnit4, listeners, out, err, parameters, testRunner, children, result, forkTillMethod, dir, classpath, dynamicClasspath);
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

    if (myDebugSocket != null) myDebugSocket.close();
    time = System.currentTimeMillis() - time;
    if (!JUnitStarter.SM_RUNNER) new TimeSender(testRunner.getRegistry()).printHeader(time);
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
  
  private int processChildren(boolean isJUnit4,
                                     List listeners,
                                     Object out,
                                     Object err,
                                     List parameters,
                                     IdeaTestRunner testRunner,
                                     List children,
                                     int result,
                                     boolean forkTillMethod, File workingDir, String classpath, String dynamicClasspath) throws IOException, InterruptedException {
    for (int i = 0, argsLength = children.size(); i < argsLength; i++) {
      final Object child = children.get(i);
      final List childTests = testRunner.getChildTests(child);
      final int childResult;
      if (childTests.isEmpty() || !forkTillMethod) {
        final OutputObjectRegistry registry = testRunner.getRegistry();
        final int startIndex = registry != null ? registry.getKnownObject(child) : -1;
        childResult =
          runChild(isJUnit4, listeners, out, err, parameters, testRunner.getStartDescription(child), workingDir, String.valueOf(startIndex), classpath, dynamicClasspath);
      }
      else {
        childResult =
          processChildren(isJUnit4, listeners, out, err, parameters, testRunner, childTests, result, forkTillMethod, workingDir, classpath, dynamicClasspath);
      }
      result = Math.min(childResult, result);
    }
    return result;
  }

  private Socket getDebugSocket() throws IOException {
    if (myDebugSocket == null) {
      myDebugSocket = new Socket("127.0.0.1", myDebugPort);
    }
    return myDebugSocket;
  }

  private int runChild(boolean isJUnit4,
                              List listeners,
                              Object out,
                              Object err,
                              List parameters,
                              String description,
                              File workingDir,
                              String startIndex,
                              String classpath,
                              String dynamicClasspath) throws IOException, InterruptedException {
    parameters = new ArrayList(parameters);

    int debugAddress = -1;
    if (myDebugPort > -1) {
      debugAddress = findAvailableSocketPort();
      boolean found = false;
      for (int i = 0; i < parameters.size(); i++) {
        String parameter = (String)parameters.get(i);
        final String debuggerParam = "transport=dt_socket";
        final int indexOf = parameter.indexOf(debuggerParam);
        if (indexOf >= 0) {
          if (debugAddress > -1) {
            parameter = parameter.substring(0, indexOf) + "transport=dt_socket,server=n,suspend=y,address=" + debugAddress;
            parameters.set(i, parameter);
            found = true;
          }
          else {
            parameters.remove(parameter);
          }
          break;
        }
      }
      if (!found) {
        parameters.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + debugAddress);
      }
    }
    //noinspection SSBasedInspection
    final File tempFile = File.createTempFile("fork", "test");
    tempFile.deleteOnExit();
    final String testOutputPath = tempFile.getAbsolutePath();

    final ProcessBuilder builder = new ProcessBuilder();
    builder.add(parameters);
    builder.add("-classpath");
    if (dynamicClasspath.length() > 0) {
      try {
        final File classpathFile = File.createTempFile("classpath", null);
        classpathFile.deleteOnExit();
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(classpathFile), "UTF-8"));
        try {
          int idx = 0;
          while (idx < classpath.length()) {
            final int endIdx = classpath.indexOf(File.pathSeparator, idx);
            if (endIdx < 0) {
              writer.println(classpath.substring(idx));
              break;
            }
            writer.println(classpath.substring(idx, endIdx));
            idx = endIdx + File.pathSeparator.length();
          }
        }
        finally {
          writer.close();
        }

        builder.add(dynamicClasspath);
        builder.add(CommandLineWrapper.class.getName());
        builder.add(classpathFile.getAbsolutePath());
      }
      catch (Throwable e) {
        builder.add(classpath);
      }
    }
    else {
      builder.add(classpath);
    }

    builder.add(JUnitForkedStarter.class.getName());
    builder.add(testOutputPath);
    builder.add(startIndex);
    builder.add(String.valueOf(isJUnit4));
    builder.add(description);
    builder.add(listeners);
    builder.setWorkingDir(workingDir);

    if (debugAddress > -1) {
      Socket socket = getDebugSocket();
      DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
      stream.writeInt(debugAddress);
      int read = socket.getInputStream().read();
    }

    final Process exec = builder.createProcess();
    final int result = exec.waitFor();
    ForkedVMWrapper.readWrapped(testOutputPath,
                                JUnitStarter.SM_RUNNER ? ((PrintStream)out) : ((SegmentedOutputStream)out).getPrintStream(),
                                JUnitStarter.SM_RUNNER ? ((PrintStream)err) : ((SegmentedOutputStream)err).getPrintStream());
    return result;
  }

  // copied from NetUtils
  private static int findAvailableSocketPort() throws IOException {
    final ServerSocket serverSocket = new ServerSocket(0);
    try {
      int port = serverSocket.getLocalPort();
      // workaround for linux : calling close() immediately after opening socket
      // may result that socket is not closed
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (serverSocket) {
        try {
          //noinspection WaitNotInLoop
          serverSocket.wait(1);
        }
        catch (InterruptedException e) {
          System.err.println(e);
        }
      }
      return port;
    }
    finally {
      serverSocket.close();
    }
  }
}
