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
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

/**
 * Before rename or move
 *  @see com.intellij.execution.junit.JUnitConfiguration#JUNIT_START_CLASS
 *  @noinspection HardCodedStringLiteral
 */
public class JUnitStarter {
  public static final int VERSION = 5;
  public static final String IDE_VERSION = "-ideVersion";

  public static final String JUNIT3_PARAMETER = "-junit3";
  public static final String JUNIT4_PARAMETER = "-junit4";
  public static final String JUNIT5_PARAMETER = "-junit5";
  public static final String JUNIT5_KEY = "idea.is.junit5";

  private static final String SOCKET = "-socket";
  public static final String JUNIT3_RUNNER_NAME = "com.intellij.junit3.JUnit3IdeaTestRunner";
  public static final String JUNIT4_RUNNER_NAME = "com.intellij.junit4.JUnit4IdeaTestRunner";
  public static final String JUNIT5_RUNNER_NAME = "com.intellij.junit5.JUnit5IdeaTestRunner";
  private static String ourForkMode;
  private static String ourCommandFileName;
  private static String ourWorkingDirs;
  protected static int    ourCount = 1;
  public static boolean SM_RUNNER = isSmRunner();
  public static String ourRepeatCount = null;

  private static boolean isSmRunner() {
    try {
      final String property = System.getProperty("idea.junit.sm_runner");
      return property != null;
    }
    catch (SecurityException e) {
      return false;
    }
  }

  public static void main(String[] args) throws IOException {
    Vector argList = new Vector();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      argList.addElement(arg);
    }

    final ArrayList listeners = new ArrayList();
    final String[] name = new String[1];

    String agentName = processParameters(argList, listeners, name);

    if (!canWorkWithJUnitVersion(System.err, agentName)) {
      System.exit(-3);
    }
    if (!checkVersion(args, System.err)) {
      System.exit(-3);
    }

    String[] array = new String[argList.size()];
    argList.copyInto(array);
    int exitCode = prepareStreamsAndStart(array, agentName, listeners, name[0]);
    System.exit(exitCode);
  }

  private static String processParameters(Vector args, final List listeners, String[] params) {
    String agentName = isJUnit5Preferred() ? JUNIT5_RUNNER_NAME : JUNIT4_RUNNER_NAME;
    Vector result = new Vector(args.size());
    for (int i = 0; i < args.size(); i++) {
      String arg = (String)args.get(i);
      if (arg.startsWith(IDE_VERSION)) {
        //ignore
      }
      else if (arg.equals(JUNIT3_PARAMETER)){
        agentName = JUNIT3_RUNNER_NAME;
      }
      else if (arg.equals(JUNIT4_PARAMETER)) {
        agentName = JUNIT4_RUNNER_NAME;
      }
      else if (arg.equals(JUNIT5_PARAMETER)) {
        agentName = JUNIT5_RUNNER_NAME;
      }
      else {
        if (arg.startsWith("@name")) {
          params[0] = arg.substring("@name".length());
          continue;
        } else if (arg.startsWith("@w@")) {
          ourWorkingDirs = arg.substring(3);
          continue;
        } else if (arg.startsWith("@@@")) {
          final int pos = arg.indexOf(',');
          ourForkMode = arg.substring(3, pos);
          ourCommandFileName = arg.substring(pos + 1);
          continue;
        } else if (arg.startsWith("@@")) {
          if (new File(arg.substring(2)).exists()) {
            try {
              final BufferedReader reader = new BufferedReader(new FileReader(arg.substring(2)));
              String line;
              while ((line = reader.readLine()) != null) {
                listeners.add(line);
              }
            }
            catch (Exception e) {
              e.printStackTrace();
            }
          }
          continue;
        } else if (arg.startsWith(SOCKET)) {
          final int port = Integer.parseInt(arg.substring(SOCKET.length()));
          try {
            final Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);  //start collecting tests
            final DataInputStream os = new DataInputStream(socket.getInputStream());
            try {
              os.readBoolean();//wait for ready flag
            }
            finally {
              os.close();
            }
          }
          catch (IOException e) {
            e.printStackTrace();
          }

          continue;
        }

        final int count = RepeatCount.getCount(arg);
        if (count != 0) {
          ourRepeatCount = arg;
          ourCount = count;
          continue;
        }

        result.addElement(arg);
      }
    }
    args.removeAllElements();
    for (int i = 0; i < result.size(); i++) {
      String arg = (String)result.get(i);
      args.addElement(arg);
    }
    if (JUNIT3_RUNNER_NAME.equals(agentName)) {
      try {
        Class.forName("org.junit.runner.Computer");
        agentName = JUNIT4_RUNNER_NAME;
      }
      catch (ClassNotFoundException e) {
        return JUNIT3_RUNNER_NAME;
      }
    }

    try {
      final String forceJUnit3 = System.getProperty("idea.force.junit3");
      if (forceJUnit3 != null && Boolean.valueOf(forceJUnit3).booleanValue()) return JUNIT3_RUNNER_NAME;
    }
    catch (SecurityException ignored) {}
    return agentName;
  }

  public static boolean isJUnit5Preferred() {
    final String useJUnit5 = System.getProperty(JUNIT5_KEY);
    final Boolean boolValue = useJUnit5 == null ? null : Boolean.valueOf(useJUnit5);
    return boolValue != null && boolValue.booleanValue();
  }

  public static boolean checkVersion(String[] args, PrintStream printStream) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith(IDE_VERSION)) {
        int ideVersion = Integer.parseInt(arg.substring(IDE_VERSION.length(), arg.length()));
        if (ideVersion != VERSION) {
          printStream.println("Wrong agent version: " + VERSION + ". IDE expects version: " + ideVersion);
          printStream.flush();
          return false;
        } else
          return true;
      }
    }
    return false;
  }

  private static boolean canWorkWithJUnitVersion(PrintStream printStream, String agentName) {
    try {
      junitVersionChecks(agentName);
    } catch (Throwable e) {
      printStream.println("!!! JUnit version 3.8 or later expected:");
      printStream.println();
      e.printStackTrace(printStream);
      printStream.flush();
      return false;
    } finally {
      printStream.flush();
    }
    return true;
  }

  private static void junitVersionChecks(String agentName) throws ClassNotFoundException {
    Class.forName("junit.framework.ComparisonFailure");
    getAgentClass(agentName);
    //noinspection UnnecessaryFullyQualifiedName
    new junit.textui.TestRunner().setPrinter(new com.intellij.junit3.JUnit3IdeaTestRunner.MockResultPrinter());
  }

  private static int prepareStreamsAndStart(String[] args,
                                            final String agentName,
                                            ArrayList listeners,
                                            String name) {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try {
      IdeaTestRunner testRunner = (IdeaTestRunner)getAgentClass(agentName).newInstance();
      Object out = SM_RUNNER ? System.out : (Object)new SegmentedOutputStream(System.out);
      Object err = SM_RUNNER ? System.err : (Object)new SegmentedOutputStream(System.err);
      if (!SM_RUNNER) {
        System.setOut(new PrintStream((OutputStream)out));
        System.setErr(new PrintStream((OutputStream)err));
      }
      if (ourCommandFileName != null) {
        if (!"none".equals(ourForkMode) || ourWorkingDirs != null && new File(ourWorkingDirs).length() > 0) {
          final List newArgs = new ArrayList();
          newArgs.add(agentName);
          newArgs.addAll(listeners);
          PrintStream printOutputStream = SM_RUNNER ? ((PrintStream)out) : ((SegmentedOutputStream)out).getPrintStream();
          PrintStream printErrStream = SM_RUNNER ? ((PrintStream)err) : ((SegmentedOutputStream)err).getPrintStream();
          return new JUnitForkedSplitter(ourWorkingDirs, ourForkMode, printOutputStream, printErrStream, newArgs)
            .startSplitting(args, name, ourCommandFileName, ourRepeatCount);
        }
      }
      testRunner.setStreams(out, err, 0);
      return testRunner.startRunnerWithArgs(args, listeners, name, ourCount, true);
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      return -2;
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }

  static Class getAgentClass(String agentName) throws ClassNotFoundException {
    return Class.forName(agentName);
  }

  public static void printClassesList(List classNames, String packageName, String category, File tempFile) throws IOException {
    final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8"));

    try {
      writer.println(packageName); //package name
      writer.println(category); //category
      for (int i = 0; i < classNames.size(); i++) {
        writer.println(classNames.get(i));
      }
    }
    finally {
      writer.close();
    }
  }
}
