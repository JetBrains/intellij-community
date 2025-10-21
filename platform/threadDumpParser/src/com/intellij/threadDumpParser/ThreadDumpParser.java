// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.threadDumpParser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.diagnostic.EventCountDumper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.diagnostic.CoroutineDumperKt.isCoroutineDumpHeader;

@ApiStatus.Internal
public final class ThreadDumpParser {
  private static final Pattern ourThreadStartPattern = Pattern.compile("^\"(.+)\".+((?:prio=\\d+ )?(?:os_prio=[^\\s]+ )?.*tid=[^\\s]+(?: nid=[^\\s]+)?|[Ii][Dd]=\\d+) ([^\\[]+)");
  private static final Pattern ourForcedThreadStartPattern = Pattern.compile("^Thread (\\d+): \\(state = (.+)\\)");
  private static final Pattern ourYourkitThreadStartPattern = Pattern.compile("(.+) \\[([A-Z_, ]*)]");
  private static final Pattern ourYourkitThreadStartPattern2 = Pattern.compile("(.+) (?:State:)? (.+) CPU usage on sample: .+");
  private static final Pattern ourJcmdThreadStartPattern = Pattern.compile("#\\d+ \"(.*)\"(.*)");
  private static final Pattern ourJcmdStackTraceElement = Pattern.compile("\\S+\\(.+\\)");
  private static final Pattern ourThreadStatePattern = Pattern.compile("java\\.lang\\.Thread\\.State: (.+) \\((.+)\\)");
  private static final Pattern ourThreadStatePattern2 = Pattern.compile("java\\.lang\\.Thread\\.State: (.+)");
  private static final Pattern ourThreadStateCarryingVirtualPattern = Pattern.compile("Carrying virtual thread #(\\d+)");
  private static final Pattern ourWaitingForLockPattern = Pattern.compile("- waiting (on|to lock) <(.+)>");
  private static final Pattern ourParkingToWaitForLockPattern = Pattern.compile("- parking to wait for {2}<(.+)>");
  private static final @NonNls String PUMP_EVENT = "java.awt.EventDispatchThread.pumpOneEventForFilters";
  private static final Pattern ourIdleTimerThreadPattern = Pattern.compile("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at java\\.util\\.TimerThread\\.mainLoop");
  private static final Pattern ourIdleSwingTimerThreadPattern = Pattern.compile("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at javax\\.swing\\.TimerQueue\\.run");
  private static final String AT_JAVA_LANG_OBJECT_WAIT = "java.lang.Object.wait(";
  private static final String ourLockedOwnableSynchronizersHeader = "Locked ownable synchronizers";
  private static final Pattern ourLockedOwnableSynchronizersPattern = Pattern.compile("- <(0x[\\da-f]+)> \\(.*\\)");

  private static final String[] IMPORTANT_THREAD_DUMP_WORDS = ContainerUtil.ar("tid", "nid", "wait", "parking", "prio", "os_prio", "java");


  private ThreadDumpParser() {
  }

  private record ParsingResult(@NotNull List<ThreadState> threads, @Nullable StringBuilder coroutineDump) {
  }

  private static @Nullable ParsingResult tryParseAsJson(String threadDump) {
    var firstCharPos = StringUtil.skipWhitespaceOrNewLineForward(threadDump, 0);
    if (!threadDump.startsWith("{", firstCharPos)) {
      return null;
    }

    JsonNode tree;
    try {
      tree = new ObjectMapper().readTree(threadDump);
    }
    catch (JsonProcessingException e) {
      return null;
    }

    // Try to parse the output of jcmd's Thread.dump_to_file -format=json.
    List<ThreadState> result = new ArrayList<>();
    var containers = tree.path("threadDump").path("threadContainers");
    containers.elements().forEachRemaining(container -> {
      container.path("threads").elements().forEachRemaining(thread -> {
        var name = thread.path("name").asText();
        var threadState = new ThreadState(name, "unknown");

        var rawStackTrace = new StringBuilder();
        thread.path("stack").elements().forEachRemaining(ste -> {
          var text = ste.asText();
          if (!text.isEmpty()) {
            rawStackTrace.append("\n      ");
            rawStackTrace.append(text);
          }
        });
        var emptyStackTrace = rawStackTrace.isEmpty();

        // No information in JSON dump, so we have some heuristics here: either check stack trace or
        // check the name which is unlikely to be empty for platform threads in the current implementation.
        var virtual = !emptyStackTrace && rawStackTrace.indexOf("java.lang.VirtualThread.run(") != -1 ||
                      emptyStackTrace && name.isEmpty();
        if (virtual) {
          threadState.setVirtual(true);
        }

        var tid = thread.path("tid").asText();

        var stackTrace = new StringBuilder();
        stackTrace.append("#").append(tid);
        stackTrace.append(" \"").append(name).append("\"");
        if (virtual) {
          stackTrace.append(" virtual");
        }
        stackTrace.append(rawStackTrace);

        threadState.setStackTrace(stackTrace.toString(), emptyStackTrace);

        result.add(threadState);
      });
    });
    return new ParsingResult(result, null);
  }

  private static @NotNull ParsingResult parseAsText(String threadDump) {
    List<ThreadState> threads = new ArrayList<>();
    StringBuilder coroutineDump = null;
    StringBuilder lastThreadStack = new StringBuilder();
    ThreadState lastThreadState = null;
    boolean expectingThreadState = false;
    boolean haveNonEmptyStackTrace = false;
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
        threads.add(lastThreadState);
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
        if (!parsedThreadState) {
          var trimmed = line.trim();
          if (trimmed.startsWith("at") || ourJcmdStackTraceElement.matcher(trimmed).matches()) {
            haveNonEmptyStackTrace = true;
          }
        }
      }
    }
    if (lastThreadState != null) {
      lastThreadState.setStackTrace(lastThreadStack.toString(), !haveNonEmptyStackTrace);
    }
    return new ParsingResult(threads, coroutineDump);
  }

  public static @NotNull List<ThreadState> parse(String threadDump) {
    ParsingResult result = tryParseAsJson(threadDump);
    if (result == null) {
      result = parseAsText(threadDump);
    }

    List<ThreadState> threads = result.threads;
    for(ThreadState threadState: threads) {
      inferThreadStateDetail(threadState);
    }
    for(ThreadState threadState: threads) {
      String lockId = findWaitingForLock(threadState.getStackTrace());
      ThreadState lockOwner = findLockOwner(threads, lockId, true);
      if (lockOwner == null) {
        lockOwner = findLockOwner(threads, lockId, false);
      }
      if (lockOwner != null) {
        if (threadState.isAwaitedBy(lockOwner)) {
          threadState.addDeadlockedThread(lockOwner);
          lockOwner.addDeadlockedThread(threadState);
        }
        lockOwner.addWaitingThread(threadState);
      }
    }
    sortThreads(threads);

    StringBuilder coroutineDump = result.coroutineDump;
    if (coroutineDump != null) {
      ThreadState coroutineState = new ThreadState("Coroutine dump", "undefined");
      coroutineState.setStackTrace(coroutineDump.toString(), false);
      threads.add(coroutineState);
    }

    return threads;
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

  @Contract(mutates = "param1")
  public static void sortThreads(List<? extends ThreadState> result) {
    result.sort((o1, o2) -> getInterestLevel(o2) - getInterestLevel(o1));
  }

  private static @Nullable String findLockedOwnableSynchronizers(final String stackTrace) {
    if (!stackTrace.contains(ourLockedOwnableSynchronizersHeader)) {
      // It's a fast path, otherwise regex below takes too much time.
      return null;
    }

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
    if (state.getOperation() == ThreadOperation.SOCKET) {
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
        stackTrace.contains("jdk.internal.ref.CleanerImpl.run") ||
        ourIdleTimerThreadPattern.matcher(stackTrace).find() ||
        ourIdleSwingTimerThreadPattern.matcher(stackTrace).find();
  }

  public static void inferThreadStateDetail(final ThreadState threadState) {
    final String javaThreadState = threadState.getJavaThreadState();
    @NonNls String stackTrace = threadState.getStackTrace();
    if (stackTrace.contains("java.net.PlainSocketImpl.socketAccept") ||
        stackTrace.contains("java.net.PlainDatagramSocketImpl.receive") ||
        stackTrace.contains("java.net.SocketInputStream.socketRead") ||
        stackTrace.contains("java.net.PlainSocketImpl.socketConnect")) {
      threadState.setOperation(ThreadOperation.SOCKET);
    }
    else if (stackTrace.contains("java.io.FileInputStream.readBytes")) {
      threadState.setOperation(ThreadOperation.IO);
    }
    else if (stackTrace.contains("jdk.internal.vm.Continuation.run")) {
      if (Thread.State.WAITING.name().equals(javaThreadState)) {
        threadState.setOperation(ThreadOperation.CARRYING_VTHREAD);
      }
    }
    else if (stackTrace.contains("java.lang.Thread.sleep")) {
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
      if (line.contains(" virtual ")) {
        state.setVirtual(true);
      }
      return state;
    }

    m = ourForcedThreadStartPattern.matcher(line);
    if (m.matches()) {
      return new ThreadState(m.group(1), m.group(2));
    }

    m = ourJcmdThreadStartPattern.matcher(line);
    if (m.matches()) {
      var state = new ThreadState(m.group(1), "unknown");
      var suffix = m.group(2);
      state.setVirtual(suffix.contains(" virtual"));
      return state;
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
    m = ourThreadStateCarryingVirtualPattern.matcher(line);
    if (m.find()) {
      threadState.setOperation(ThreadOperation.CARRYING_VTHREAD);
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
      if (!line.matches("\\s+.*") && !lastLine.isEmpty()) {
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
