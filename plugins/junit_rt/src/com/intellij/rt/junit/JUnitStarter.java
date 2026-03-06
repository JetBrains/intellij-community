// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.junit;

import com.intellij.rt.execution.junit.RepeatCount;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Before rename or move
 *
 * @see com.intellij.execution.junit.JUnitConfiguration#JUNIT_START_CLASS
 */
public final class JUnitStarter {
  public static final int VERSION = 5;
  public static final String IDE_VERSION = "-ideVersion";

  public static final String JUNIT3_PARAMETER = "-junit3";
  public static final String JUNIT4_PARAMETER = "-junit4";
  public static final String JUNIT5_PARAMETER = "-junit5";
  public static final String JUNIT6_PARAMETER = "-junit6";
  private static final String JUNIT5_KEY = "idea.is.junit5";
  private static final String JUNIT3_KEY = "idea.force.junit3";

  private static final String SOCKET = "-socket";
  private static String ourForkMode;
  private static String ourCommandFileName;
  private static String ourWorkingDirs;
  static int ourCount = 1;
  public static String ourRepeatCount;

  public static void main(String[] args) {
    List<String> argList = new ArrayList<>(Arrays.asList(args));

    final ArrayList<String> listeners = new ArrayList<>();
    final String[] name = new String[1];

    JUnitRunner runner = processParameters(argList, listeners, name);

    if (!checkVersion(args, System.err)) {
      System.exit(-3);
    }

    @SuppressWarnings("SSBasedInspection")
    String[] array = argList.toArray(new String[0]);
    int exitCode = prepareStreamsAndStart(array, runner.getRunnerName(), listeners, name[0]);
    System.exit(exitCode);
  }

  private static JUnitRunner processParameters(List<String> args, final List<? super String> listeners, String[] params) {
    JUnitRunner runner = isJUnit5Preferred() ? JUnitRunner.JUNIT5 : JUnitRunner.JUNIT4;
    List<String> result = new ArrayList<>(args.size());
    for (String arg : args) {
      if (arg.startsWith(IDE_VERSION)) {
        //ignore
      }
      else {
        JUnitRunner junitRunner = JUnitRunner.of(arg);
        if (junitRunner != null) {
          runner = junitRunner;
          continue;
        }
        else if (arg.startsWith("@name")) {
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

    try {
      if (Boolean.getBoolean(JUNIT3_KEY)) {
        return JUnitRunner.JUNIT3;
      }
    }
    catch (SecurityException ignore) {
    }

    switch (runner) {
      case JUNIT6:
        if (JUnitRunner.JUNIT6.check()) return JUnitRunner.JUNIT6;
      case JUNIT5:
        if (JUnitRunner.JUNIT5.check()) return JUnitRunner.JUNIT5;
        System.err.println("!!! JUnit Platform is not available on the classpath");
        System.err.flush();
        System.exit(-3);
      case JUNIT4:
      case JUNIT3:
        if (JUnitRunner.JUNIT4.check()) {
          return JUnitRunner.JUNIT4;
        }
        else {
          return JUnitRunner.JUNIT3;
        }
    }
    return runner;
  }

  private static boolean isJUnit5Preferred() {
    try {
      return Boolean.getBoolean(JUNIT5_KEY);
    }
    catch (SecurityException ignore) {
      return false;
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

    try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(tempFile.toPath()), StandardCharsets.UTF_8))) {
      writer.println(packageName); //package name
      writer.println(category); //category
      writer.println(filters); //patterns
      for (String name : classNames) {
        writer.println(name);
      }
    }
  }

  private enum JUnitRunner {
    JUNIT3(JUNIT3_PARAMETER, "com.intellij.junit3.JUnit3IdeaTestRunner") {
      @Override
      public boolean check() {
        return true;
      }
    },
    JUNIT4(JUNIT4_PARAMETER, "com.intellij.junit4.JUnit4IdeaTestRunner") {
      @Override
      public boolean check() {
        try {
          Class.forName("junit.framework.ComparisonFailure");
          Class.forName("junit.textui.TestRunner");
          Class.forName("org.junit.Test");
          Class.forName(getRunnerName());
          return true;
        }
        catch (ClassNotFoundException e) {
          return false;
        }
      }
    },
    JUNIT5(JUNIT5_PARAMETER, "com.intellij.junit5.JUnit5IdeaTestRunner") {
      @Override
      public boolean check() {
        try {
          Class.forName("org.junit.platform.engine.TestEngine");
          Class.forName(getRunnerName());
          return true;
        }
        catch (ClassNotFoundException e) {
          return false;
        }
      }
    },
    JUNIT6(JUNIT6_PARAMETER, "com.intellij.junit6.JUnit6IdeaTestRunner") {
      @Override
      public boolean check() {
        try {
          Class.forName(getRunnerName());
          String engineJar = getClassLocation("org.junit.platform.engine.TestEngine");
          String engine6Jar = getClassLocation("org.junit.platform.engine.CancellationToken");
          return Objects.equals(engineJar, engine6Jar);
        }
        catch (ClassNotFoundException e) {
          return false;
        }
      }
    };

    private final String parameter;
    private final String runnerName;

    JUnitRunner(String parameter, String runnerName) {
      this.parameter = parameter;
      this.runnerName = runnerName;
    }

    /**
     * Verifies that all required classes for this runner are present on the classpath.
     *
     * @return true if all required classes are present, false otherwise
     */
    public abstract boolean check();

    public String getRunnerName() {
      return runnerName;
    }

    public static JUnitRunner of(String parameter) {
      for (JUnitRunner runner : values()) {
        if (runner.parameter.equals(parameter)) return runner;
      }
      return null;
    }

    private static String getClassLocation(String className) {
      try {
        CodeSource cs = Class.forName(className).getProtectionDomain().getCodeSource();
        return cs != null ? cs.getLocation().toExternalForm() : null;
      }
      catch (ClassNotFoundException | SecurityException e) {
        return null;
      }
    }
  }
}
