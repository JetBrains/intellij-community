// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.junit;

import com.intellij.rt.execution.junit.RepeatCount;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Before rename or move
 *
 * @noinspection HardCodedStringLiteral
 * @see com.intellij.execution.junit.JUnitConfiguration#JUNIT_START_CLASS
 */
public final class JUnitStarter {
  public static final int VERSION = 5;
  public static final String IDE_VERSION = "-ideVersion";

  public static final String JUNIT3_PARAMETER = "-junit3";
  public static final String JUNIT4_PARAMETER = "-junit4";
  public static final String JUNIT5_PARAMETER = "-junit5";
  private static final String JUNIT5_KEY = "idea.is.junit5";

  private static final String SOCKET = "-socket";
  private static final String JUNIT3_RUNNER_NAME = "com.intellij.junit3.JUnit3IdeaTestRunner";
  private static final String JUNIT4_RUNNER_NAME = "com.intellij.junit4.JUnit4IdeaTestRunner";
  private static final String JUNIT5_RUNNER_NAME = "com.intellij.junit5.JUnit5IdeaTestRunner";
  private static String ourForkMode;
  private static String ourCommandFileName;
  private static String ourWorkingDirs;
  static int ourCount = 1;
  public static String ourRepeatCount;

  public static void main(String[] args) {
    List<String> argList = new ArrayList<>(Arrays.asList(args));

    final ArrayList<String> listeners = new ArrayList<>();
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

  private static String processParameters(List<String> args, final List<? super String> listeners, String[] params) {
    String agentName = isJUnit5Preferred() ? JUNIT5_RUNNER_NAME : JUNIT4_RUNNER_NAME;
    List<String> result = new ArrayList<>(args.size());
    for (String arg : args) {
      if (arg.startsWith(IDE_VERSION)) {
        //ignore
      }
      else if (arg.equals(JUNIT3_PARAMETER)) {
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
        }
        else if (arg.startsWith("@w@")) {
          ourWorkingDirs = arg.substring(3);
          continue;
        }
        else if (arg.startsWith("@@@")) {
          final int pos = arg.indexOf(',');
          ourForkMode = arg.substring(3, pos);
          ourCommandFileName = arg.substring(pos + 1);
          continue;
        }
        else if (arg.startsWith("@@")) {
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
        }
        else if (arg.startsWith(SOCKET)) {
          // the form of "-socket[<host>:]<port>" is expected here
          // for example "-sockethost.docker.internal:12345" or "-socket54321"
          final String value = arg.substring(SOCKET.length());
          final String host;
          final int port;
          // NB the host might be an IPv6 address (and this kind of address contains ":")
          int index = value.lastIndexOf(':');
          if (index == -1) {
            host = "127.0.0.1";
            port = Integer.parseInt(value);
          }
          else {
            host = value.substring(0, index);
            port = Integer.parseInt(value.substring(index + 1));
          }
          try {
            final Socket socket = new Socket(InetAddress.getByName(host), port);  //start collecting tests
            try (DataInputStream os = new DataInputStream(socket.getInputStream())) {
              os.readBoolean();//wait for ready flag
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
      if (Boolean.parseBoolean(forceJUnit3)) return JUNIT3_RUNNER_NAME;
    }
    catch (SecurityException ignored) {
    }
    return agentName;
  }

  private static boolean isJUnit5Preferred() {
    return Boolean.parseBoolean(System.getProperty(JUNIT5_KEY));
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
    }
    catch (Throwable e) {
      printStream.println("!!! JUnit version 3.8 or later expected:");
      printStream.println();
      e.printStackTrace(printStream);
      printStream.flush();
      return false;
    }
    finally {
      printStream.flush();
    }
    return true;
  }

  private static void junitVersionChecks(String agentName) throws ClassNotFoundException {
    Class.forName("junit.framework.ComparisonFailure");
    getAgentClass(agentName);
    Class.forName("junit.textui.TestRunner");
  }

  private static int prepareStreamsAndStart(String[] args,
                                            final String agentName,
                                            ArrayList<String> listeners,
                                            String name) {
    try {
      IdeaTestRunner<?> testRunner = (IdeaTestRunner<?>)getAgentClass(agentName).newInstance();
      if (ourCommandFileName != null) {
        if (!"none".equals(ourForkMode) || ourWorkingDirs != null && new File(ourWorkingDirs).length() > 0) {
          final List<String> newArgs = new ArrayList<>();
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

  public static void printClassesList(List<String> classNames, String packageName, String category, String filters, File tempFile)
    throws IOException {

    try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
      writer.println(packageName); //package name
      writer.println(category); //category
      writer.println(filters); //patterns
      for (String name : classNames) {
        writer.println(name);
      }
    }
  }
}
