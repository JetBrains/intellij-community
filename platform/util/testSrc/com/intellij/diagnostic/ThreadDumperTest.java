/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ThreadDumperTest {
  @Test
  public void testParser() {
    String stackTrace = "\"AWT-EventQueue-0 2.3#__BUILD_NUMBER__ Studio, eap:true, os:Mac OS X 10.11.5, java-version:Oracle Corporation 1.8.0_73-b02\" prio=0 tid=0x0 nid=0x0 runnable\n" +
                        "     java.lang.Thread.State: RUNNABLE\n" +
                        "\n" +
                        "\tat java.io.UnixFileSystem.canonicalize0(Native Method)\n" +
                        "\tat java.io.UnixFileSystem.canonicalize(UnixFileSystem.java:172)\n" +
                        "\tat java.io.File.getCanonicalPath(File.java:618)\n" +
                        "\tat java.io.File.getCanonicalFile(File.java:643)\n" +
                        "\n" +
                        "\"ApplicationImpl pooled thread 7\" prio=0 tid=0x0 nid=0x0 runnable\n" +
                        "     java.lang.Thread.State: RUNNABLE\n" +
                        "\n" +
                        "\tat sun.management.ThreadImpl.dumpThreads0(Native Method)\n" +
                        "\tat sun.management.ThreadImpl.dumpAllThreads(ThreadImpl.java:446)\n" +
                        "\tat com.intellij.diagnostic.ThreadDumper.dumpThreadsToFile(ThreadDumper.java:58)\n";

    assertEquals("com.android.ApplicationNotResponding: AWT-EventQueue-0 RUNNABLE\n" +
                 "\tat java.io.UnixFileSystem.canonicalize0(Native Method)\n" +
                 "\tat java.io.UnixFileSystem.canonicalize(UnixFileSystem.java:172)\n" +
                 "\tat java.io.File.getCanonicalPath(File.java:618)\n" +
                 "\tat java.io.File.getCanonicalFile(File.java:643)",
                 ThreadDumper.getEdtStackForCrash(Arrays.asList(stackTrace.split("\n"))));

    stackTrace = "\"AWT-EventQueue-0 2.3#__BUILD_NUMBER__ Studio, eap:true, os:Linux 3.13.0-93-generic, java-version:Oracle Corporation 1.8.0_60-b27\" prio=0 tid=0x0 nid=0x0 waiting on condition\n" +
                 "     java.lang.Thread.State: WAITING\n" +
                 " on java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject@4fd95514\n" +
                 "\tat sun.misc.Unsafe.park(Native Method)\n" +
                 "\tat java.util.concurrent.locks.LockSupport.park(LockSupport.java:175)\n";
    assertEquals("com.android.ApplicationNotResponding: AWT-EventQueue-0 WAITING on java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject@4fd95514\n" +
                 "\tat sun.misc.Unsafe.park(Native Method)\n" +
                 "\tat java.util.concurrent.locks.LockSupport.park(LockSupport.java:175)",
                 ThreadDumper.getEdtStackForCrash(Arrays.asList(stackTrace.split("\n"))));
  }
}
