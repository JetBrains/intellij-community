// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.threadDumpParser;

import com.intellij.diagnostic.EventCountDumper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.diagnostic.CoroutineDumperKt.isCoroutineDumpHeader;

@ApiStatus.Internal
public final class ThreadDumpParser {
  private static final Pattern ourThreadStartPattern = Pattern.compile("^\"(.+)\".+(prio=\\d+ (?:os_prio=[^\\s]+ )?.*tid=[^\\s]+ nid=[^\\s]+|[Ii][Dd]=\\d+) ([^\\[]+)");
  private static final Pattern ourForcedThreadStartPattern = Pattern.compile("^Thread (\\d+): \\(state = (.+)\\)");
  private static final Pattern ourYourkitThreadStartPattern = Pattern.compile("(.+) \\[([A-Z_, ]*)]");
  private static final Pattern ourYourkitThreadStartPattern2 = Pattern.compile("(.+) (?:State:)? (.+) CPU usage on sample: .+");
  private static final Pattern ourThreadStatePattern = Pattern.compile("java\\.lang\\.Thread\\.State: (.+) \\((.+)\\)");
  private static final Pattern ourThreadStatePattern2 = Pattern.compile("java\\.lang\\.Thread\\.State: (.+)");
  private static final Pattern ourWaitingForLockPattern = Pattern.compile("- waiting (on|to lock) <(.+)>");
  private static final Pattern ourParkingToWaitForLockPattern = Pattern.compile("- parking to wait for {2}<(.+)>");
  private static final @NonNls String PUMP_EVENT = "java.awt.EventDispatchThread.pumpOneEventForFilters";
  private static final Pattern ourIdleTimerThreadPattern = Pattern.compile("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at java\\.util\\.TimerThread\\.mainLoop");
  private static final Pattern ourIdleSwingTimerThreadPattern = Pattern.compile("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at javax\\.swing\\.TimerQueue\\.run");
  private static final String AT_JAVA_LANG_OBJECT_WAIT = "at java.lang.Object.wait(";
  private static final Pattern ourLockedOwnableSynchronizersPattern = Pattern.compile("- <(0x[\\da-f]+)> \\(.*\\)");

  private static final String[] IMPORTANT_THREAD_DUMP_WORDS = ContainerUtil.ar("tid", "nid", "wait", "parking", "prio", "os_prio", "java");


  private ThreadDumpParser() {
  }

  public static @NotNull List<ThreadState> parse(String threadDump) {
    List<ThreadState> result = new ArrayList<>();
    StringBuilder lastThreadStack = new StringBuilder();
    ThreadState lastThreadState = null;
    boolean expectingThreadState = false;
    boolean haveNonEmptyStackTrace = false;
    StringBuilder coroutineDump = null;
    for(@NonNls String line: StringUtil.tokenize(threadDump, "\r\n")) {
      if (EventCountDumper.EVENT_COUNTS_HEADER.equals(line)) {
        break;
      }
      if (isCoroutineDumpHeader(line)) {
        coroutineDump = new StringBuilder();
      }
      if (coroutineDump != null) {
        coroutineDump.append(line).append("\n");
        continue;
      }
      if (line.startsWith("============") || line.contains("Java-level deadlock")) {
        break;
      }
      ThreadState state = tryParseThreadStart(line.trim());
      if (state != null) {
        if (lastThreadState != null) {
          lastThreadState.setStackTrace(lastThreadStack.toString(), !haveNonEmptyStackTrace);
        }
        lastThreadState = state;
        result.add(lastThreadState);
        lastThreadStack.setLength(0);
        haveNonEmptyStackTrace = false;
        lastThreadStack.append(line).append("\n");
        expectingThreadState = true;
      }
      else {
        boolean parsedThreadState = false;
        if (expectingThreadState) {
          expectingThreadState = false;
          parsedThreadState = tryParseThreadState(line, lastThreadState);
        }
        lastThreadStack.append(line).append("\n");
        if (!parsedThreadState && line.trim().startsWith("at")) {
          haveNonEmptyStackTrace = true;
        }
      }
    }
    if (lastThreadState != null) {
      lastThreadState.setStackTrace(lastThreadStack.toString(), !haveNonEmptyStackTrace);
    }
    for(ThreadState threadState: result) {
      inferThreadStateDetail(threadState);
    }
    for(ThreadState threadState: result) {
      String lockId = findWaitingForLock(threadState.getStackTrace());
      ThreadState lockOwner = findLockOwner(result, lockId, true);
      if (lockOwner == null) {
        lockOwner = findLockOwner(result, lockId, false);
      }
      if (lockOwner != null) {
        if (threadState.isAwaitedBy(lockOwner)) {
          threadState.addDeadlockedThread(lockOwner);
          lockOwner.addDeadlockedThread(threadState);
        }
        lockOwner.addWaitingThread(threadState);
      }
    }
    sortThreads(result);
    if (coroutineDump != null) {
      ThreadState coroutineState = new ThreadState("Coroutine dump", "undefined");
      coroutineState.setStackTrace(coroutineDump.toString(), false);
      result.add(coroutineState);
    }
    return result;
  }

  private static @Nullable ThreadState findLockOwner(List<? extends ThreadState> result, @Nullable String lockId, boolean ignoreWaiting) {
    if (lockId == null) return null;

    final String marker = "- locked <" + lockId + ">";
    for(ThreadState lockOwner : result) {
      String trace = lockOwner.getStackTrace();
      if (trace.contains(marker) && (!ignoreWaiting || !trace.contains(AT_JAVA_LANG_OBJECT_WAIT))) {
        return lockOwner;
      }
    }
    for(ThreadState lockOwner : result) {
      if(lockOwner.getOwnableSynchronizers() != null && lockOwner.getOwnableSynchronizers().equals(lockId)){
        return lockOwner;
      }
    }
    return null;
  }

  public static void sortThreads(List<? extends ThreadState> result) {
    result.sort((o1, o2) -> getInterestLevel(o2) - getInterestLevel(o1));
  }

  private static @Nullable String findLockedOwnableSynchronizers(final String stackTrace) {
    Matcher m = ourLockedOwnableSynchronizersPattern.matcher(stackTrace);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  private static @Nullable String findWaitingForLock(final String stackTrace) {
    Matcher m = ourWaitingForLockPattern.matcher(stackTrace);
    if (m.find()) {
      return m.group(2);
    }
    m = ourParkingToWaitForLockPattern.matcher(stackTrace);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  private static int getInterestLevel(final ThreadState state) {
    if (state.isEmptyStackTrace()) return -10;
    if (state.isKnownJDKThread()) return -5;
    if (state.isSleeping()) {
      return -2;
    }
    if (state.getOperation() == ThreadOperation.Socket) {
      return -1;
    }
    return state.getStackDepth();
  }

  static boolean isKnownJdkThread(@NotNull String stackTrace) {
    return stackTrace.contains("java.lang.ref.Reference$ReferenceHandler.run") ||
        stackTrace.contains("java.lang.ref.Finalizer$FinalizerThread.run") ||
        stackTrace.contains("sun.awt.AWTAutoShutdown.run") ||
        stackTrace.contains("sun.java2d.Disposer.run") ||
        stackTrace.contains("sun.awt.windows.WToolkit.eventLoop") ||
        ourIdleTimerThreadPattern.matcher(stackTrace).find() ||
        ourIdleSwingTimerThreadPattern.matcher(stackTrace).find();
  }

  public static void inferThreadStateDetail(final ThreadState threadState) {
    @NonNls String stackTrace = threadState.getStackTrace();
    if (stackTrace.contains("at java.net.PlainSocketImpl.socketAccept") ||
        stackTrace.contains("at java.net.PlainDatagramSocketImpl.receive") ||
        stackTrace.contains("at java.net.SocketInputStream.socketRead") ||
        stackTrace.contains("at java.net.PlainSocketImpl.socketConnect")) {
      threadState.setOperation(ThreadOperation.Socket);
    }
    else if (stackTrace.contains("at java.io.FileInputStream.readBytes")) {
      threadState.setOperation(ThreadOperation.IO);
    }
    else if (stackTrace.contains("at java.lang.Thread.sleep")) {
      final String javaThreadState = threadState.getJavaThreadState();
      if (!Thread.State.RUNNABLE.name().equals(javaThreadState)) {
        threadState.setThreadStateDetail("sleeping");   // JDK 1.6 sets this explicitly, but JDK 1.5 does not
      }
    }
    if (threadState.isEDT()) {
      if (stackTrace.contains("java.awt.EventQueue.getNextEvent")) {
        threadState.setThreadStateDetail("idle");
      }
      int modality = 0;
      int pos = 0;
      while(true) {
        pos = stackTrace.indexOf(PUMP_EVENT, pos);
        if (pos < 0) break;
        modality++;
        pos += PUMP_EVENT.length();
      }
      threadState.setExtraState("modality level " + modality);
    }
    threadState.setOwnableSynchronizers(findLockedOwnableSynchronizers(threadState.getStackTrace()));
  }

  private static @Nullable ThreadState tryParseThreadStart(String line) {
    Matcher m = ourThreadStartPattern.matcher(line);
    if (m.find()) {
      final ThreadState state = new ThreadState(m.group(1), m.group(3));
      if (line.contains(" daemon ")) {
        state.setDaemon(true);
      }
      return state;
    }

    m = ourForcedThreadStartPattern.matcher(line);
    if (m.matches()) {
      return new ThreadState(m.group(1), m.group(2));
    }

    boolean daemon = line.contains(" [DAEMON]");
    if (daemon) {
      line = StringUtil.replace(line, " [DAEMON]", "");
    }

    m = matchYourKit(line);
    if (m != null) {
      ThreadState state = new ThreadState(m.group(1), m.group(2));
      state.setDaemon(daemon);
      return state;
    }
    return null;
  }

  private static @Nullable Matcher matchYourKit(String line) {
    if (line.contains("[")) {
      Matcher m = ourYourkitThreadStartPattern.matcher(line);
      if (m.matches()) return m;
    }

    if (line.contains("CPU usage on sample:")) {
      Matcher m = ourYourkitThreadStartPattern2.matcher(line);
      if (m.matches()) return m;
    }

    return null;
  }

  private static boolean tryParseThreadState(final String line, final ThreadState threadState) {
    Matcher m = ourThreadStatePattern.matcher(line);
    if (m.find()) {
      threadState.setJavaThreadState(m.group(1));
      threadState.setThreadStateDetail(m.group(2).trim());
      return true;
    }
    m = ourThreadStatePattern2.matcher(line);
    if (m.find()) {
      threadState.setJavaThreadState(m.group(1));
      return true;
    }
    return false;
  }

  public static String normalizeText(@NonNls String text) {
    StringBuilder builder = new StringBuilder(text.length());

    text = text.replaceAll("(\\S[ \\t\\x0B\\f\\r]+)(at\\s+)", "$1\n$2");
    text = text.replaceAll("(\\\\n|\\\\r|\\\\t)+(at\\s+)", "\n$2");
    String[] lines = text.split("\n");

    boolean first = true;
    boolean inAuxInfo = false;
    for (final String line : lines) {
      //noinspection HardCodedStringLiteral
      if (!inAuxInfo && (line.startsWith("JNI global references") || line.trim().equals("Heap"))) {
        builder.append("\n");
        inAuxInfo = true;
      }
      if (inAuxInfo) {
        builder.append(trimSuffix(line)).append("\n");
        continue;
      }
      if (line.startsWith("at breakpoint")) { // possible thread status mixed with "at ..."
        builder.append(" ").append(trimSuffix(line));
        continue;
      }
      if (!first && (mustHaveNewLineBefore(line) || StringUtil.endsWith(builder, ")"))) {
        if (!StringUtil.endsWith(builder, "\n")) builder.append("\n");
        if (line.startsWith("\"")) builder.append("\n"); // Additional line break for thread names
      }
      first = false;
      int i = builder.lastIndexOf("\n");
      CharSequence lastLine = i == -1 ? builder : builder.subSequence(i + 1, builder.length());
      if (!line.matches("\\s+.*") && lastLine.length() > 0) {
        if (lastLine.toString().matches("\\s*at") //separate 'at' from filename
            || ContainerUtil.or(IMPORTANT_THREAD_DUMP_WORDS, word -> line.startsWith(word))) {
          builder.append(" ");
        }
      }
      builder.append(trimSuffix(line));
    }
    return builder.toString();
  }

  private static boolean mustHaveNewLineBefore(String line) {
    final int nonWs = CharArrayUtil.shiftForward(line, 0, " \t");
    if (nonWs < line.length()) {
      line = line.substring(nonWs);
    }

    if (line.startsWith("at")) return true;        // Start of the new stack frame entry
    if (line.startsWith("Caused")) return true;    // Caused by message
    if (line.startsWith("- locked")) return true;  // "Locked a monitor" logging
    if (line.startsWith("- waiting")) return true; // "Waiting for monitor" logging
    if (line.startsWith("- parking to wait")) return true;
    if (line.startsWith("java.lang.Thread.State")) return true;
    if (line.startsWith("\"")) return true;        // Start of the new thread (thread name)

    return false;
  }

  private static String trimSuffix(final String line) {
    int len = line.length();

    while ((0 < len) && (line.charAt(len - 1) <= ' ')) {
      len--;
    }
    return (len < line.length()) ? line.substring(0, len) : line;
  }

}
