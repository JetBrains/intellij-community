// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.junit;

import com.intellij.rt.execution.junit.RepeatCount;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
  public static String ourRepeatCount = null;

  public static void main(String[] args) {
    List<String> argList = new ArrayList<String>(Arrays.asList(args));

    final ArrayList<String> listeners = new ArrayList<String>();
    final String[] name = new String[1];

    String agentName = processParameters(argList, listeners, name);

    if (!JUNIT5_RUNNER_NAME.equals(agentName) && !canWorkWithJUnitVersion(System.err, agentName)) {
      System.exit(-3);
    }
    if (!checkVersion(args, System.err)) {
      System.exit(-3);
    }

    String[] array = argList.toArray(new String[0]);
    int exitCode = prepareStreamsAndStart(array, agentName, listeners, name[0]);
    System.exit(exitCode);
  }

  private static String processParameters(List<String> args, final List<String> listeners, String[] params) {
    String agentName = isJUnit5Preferred() ? JUNIT5_RUNNER_NAME : JUNIT4_RUNNER_NAME;
    List<String> result = new ArrayList<String>(args.size());
    for (String arg : args) {
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

        result.add(arg);
      }
    }
    args.clear();
    args.addAll(result);
    if (JUNIT3_RUNNER_NAME.equals(agentName)) {
      try {
        Class.forName("org.junit.runner.Computer");
        agentName = JUNIT4_RUNNER_NAME;
      }
      catch (ClassNotFoundException e) {
        return JUNIT3_RUNNER_NAME;
      }
    }

    if (JUNIT4_RUNNER_NAME.equals(agentName)) {
      try {
        Class.forName("org.junit.Test");
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
    if (useJUnit5 == null) {
      return false;
    }
    else {
      final Boolean boolValue = Boolean.valueOf(useJUnit5);
      return boolValue != null && boolValue.booleanValue();
    }
  }

  public static boolean checkVersion(String[] args, PrintStream printStream) {
    for (String arg : args) {
      if (arg.startsWith(IDE_VERSION)) {
        int ideVersion = Integer.parseInt(arg.substring(IDE_VERSION.length()));
        if (ideVersion != VERSION) {
          printStream.println("Wrong agent version: " + VERSION + ". IDE expects version: " + ideVersion);
          printStream.flush();
          return false;
        }
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
    new junit.textui.TestRunner().setPrinter(null); //
  }

  private static int prepareStreamsAndStart(String[] args,
                                            final String agentName,
                                            ArrayList<String> listeners,
                                            String name) {
    try {
      IdeaTestRunner<?> testRunner = (IdeaTestRunner<?>)getAgentClass(agentName).newInstance();
      if (ourCommandFileName != null) {
        if (!"none".equals(ourForkMode) || ourWorkingDirs != null && new File(ourWorkingDirs).length() > 0) {
          final List<String> newArgs = new ArrayList<String>();
          newArgs.add(agentName);
          newArgs.addAll(listeners);
          return new JUnitForkedSplitter(ourWorkingDirs, ourForkMode, newArgs)
            .startSplitting(args, name, ourCommandFileName, ourRepeatCount);
        }
      }
      return IdeaTestRunner.Repeater.startRunnerWithArgs(testRunner, args, listeners, name, ourCount, true);
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      return -2;
    }

  }

  static Class<?> getAgentClass(String agentName) throws ClassNotFoundException {
    return Class.forName(agentName);
  }

  public static void printClassesList(List<String> classNames, String packageName, String category, String filters, File tempFile) throws IOException {
    final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8"));

    try {
      writer.println(packageName); //package name
      writer.println(category); //category
      writer.println(filters); //patterns
      for (String name : classNames) {
        writer.println(name);
      }
    }
    finally {
      writer.close();
    }
  }
}
