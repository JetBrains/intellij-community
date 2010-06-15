/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.junit3.JUnit3IdeaTestRunner;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import junit.textui.TestRunner;

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
  public static final String JUNIT4_PARAMETER = "-junit4";
  private static final String SOCKET = "-socket";

  public static void main(String[] args) throws IOException {
    SegmentedOutputStream out = new SegmentedOutputStream(System.out);
    SegmentedOutputStream err = new SegmentedOutputStream(System.err);
    Vector argList = new Vector();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      argList.addElement(arg);
    }

    final ArrayList listeners = new ArrayList();
    boolean isJUnit4 = processParameters(argList, listeners);

    if (!canWorkWithJUnitVersion(err, isJUnit4)) {
      err.flush();
      System.exit(-3);
    }
    if (!checkVersion(args, err)) {
      err.flush();
      System.exit(-3);
    }

    String[] array = new String[argList.size()];
    argList.copyInto(array);
    int exitCode = prepareStreamsAndStart(array, isJUnit4, listeners, out, err);
    System.exit(exitCode);
  }

  private static boolean processParameters(Vector args, final List listeners) {
    boolean isJunit4 = false;
    String tempFilePath = null;
    Vector result = new Vector(args.size());
    for (int i = 0; i < args.size(); i++) {
      String arg = (String)args.get(i);
      if (arg.startsWith(IDE_VERSION)) {
        //ignore
      }
      else if (arg.equals(JUNIT4_PARAMETER)){
        isJunit4 = true;
      }
      else {
        if (arg.startsWith("@@")) {
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
        } else if (arg.startsWith("@")) {
          tempFilePath = arg.substring(1);
        } else if (arg.startsWith(SOCKET)) {
          final int port = Integer.parseInt(arg.substring(SOCKET.length()));
          try {
            final Socket socket = new Socket(InetAddress.getLocalHost(), port);  //start collecting tests
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

          isJunit4 = isJUnit4(isJunit4, tempFilePath);
          continue;
        }
        result.addElement(arg);
      }
    }
    if (tempFilePath != null && !args.contains(SOCKET)) {
      isJunit4 = isJUnit4(isJunit4, tempFilePath);
    }
    args.removeAllElements();
    for (int i = 0; i < result.size(); i++) {
      String arg = (String)result.get(i);
      args.addElement(arg);
    }
    return isJunit4;
  }

  private static boolean isJUnit4(boolean junit4, String tempFilePath) {
    try {
      BufferedReader reader = new BufferedReader(new FileReader(tempFilePath));
      try {
        junit4 |= JUNIT4_PARAMETER.equals(reader.readLine());
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return junit4;
  }

  public static boolean checkVersion(String[] args, SegmentedOutputStream notifications) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith(IDE_VERSION)) {
        int ideVersion = Integer.parseInt(arg.substring(IDE_VERSION.length(), arg.length()));
        if (ideVersion != VERSION) {
          PrintStream stream = new PrintStream(notifications);
          stream.println("Wrong agent version: " + VERSION + ". IDE expects version: " + ideVersion);
          stream.flush();
          return false;
        } else
          return true;
      }
    }
    return false;
  }

  private static boolean canWorkWithJUnitVersion(OutputStream notifications, boolean isJUnit4) {
    final PrintStream stream = new PrintStream(notifications);
    try {
      junitVersionChecks(isJUnit4);
    } catch (Throwable e) {
      stream.println("!!! JUnit version 3.8 or later expected:");
      stream.println();
      e.printStackTrace(stream);
      stream.flush();
      return false;
    } finally {
      stream.flush();
    }
    return true;
  }

  private static void junitVersionChecks(boolean isJUnit4) throws ClassNotFoundException {
    Class.forName("junit.framework.ComparisonFailure");
    getAgentClass(isJUnit4);
    new TestRunner().setPrinter(new JUnit3IdeaTestRunner.MockResultPrinter());
  }

  private static int prepareStreamsAndStart(String[] args, final boolean isJUnit4, ArrayList listeners, SegmentedOutputStream out,
                                            SegmentedOutputStream err) {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    int result;
    try {
      System.setOut(new PrintStream(out));
      System.setErr(new PrintStream(err));
      IdeaTestRunner testRunner = (IdeaTestRunner)getAgentClass(isJUnit4).newInstance();
      testRunner.setStreams(out, err);
      result = testRunner.startRunnerWithArgs(args, listeners);
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      result = -2;
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return result;
  }

  private static Class getAgentClass(boolean isJUnit4) throws ClassNotFoundException {
    return isJUnit4
           ? Class.forName("com.intellij.junit4.JUnit4IdeaTestRunner")
           : Class.forName("com.intellij.junit3.JUnit3IdeaTestRunner");

  }
}
