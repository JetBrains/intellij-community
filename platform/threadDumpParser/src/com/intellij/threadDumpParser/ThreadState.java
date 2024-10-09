// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.threadDumpParser;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public class ThreadState {
  private final String myName;
  private final String myState;
  private String myStackTrace;
  private boolean myEmptyStackTrace;
  private String myJavaThreadState;
  private String myThreadStateDetail;
  private String myExtraState;
  private boolean isDaemon;
  private final Set<ThreadState> myThreadsWaitingForMyLock = new HashSet<>();
  private final Set<ThreadState> myDeadlockedThreads = new HashSet<>();
  private String ownableSynchronizers;

  private @Nullable ThreadOperation myOperation;
  private Boolean myKnownJDKThread;
  private int myStackDepth;

  public ThreadState(final String name, final String state) {
    myName = name;
    myState = state.trim();
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public @NlsSafe String getState() {
    return myState;
  }

  public @NlsSafe String getStackTrace() {
    return myStackTrace;
  }

  public void setStackTrace(@NotNull String stackTrace, boolean isEmpty) {
    myStackTrace = stackTrace;
    myEmptyStackTrace = isEmpty;
    myKnownJDKThread = null;
    myStackDepth = StringUtil.countNewLines(myStackTrace);
  }

  int getStackDepth() {
    return myStackDepth;
  }

  public boolean isKnownJDKThread() {
    String stackTrace = myStackTrace;
    if (stackTrace == null) {
      return false;
    }
    if (myKnownJDKThread == null) {
      myKnownJDKThread = ThreadDumpParser.isKnownJdkThread(stackTrace);
    }
    return myKnownJDKThread;
  }

  public Collection<ThreadState> getAwaitingThreads() {
    return Collections.unmodifiableSet(myThreadsWaitingForMyLock);
  }

  public String toString() {
    return myName;
  }

  public void setJavaThreadState(final String javaThreadState) {
    myJavaThreadState = javaThreadState;
  }

  public void setThreadStateDetail(final @NonNls String threadStateDetail) {
    myThreadStateDetail = threadStateDetail;
  }

  public String getJavaThreadState() {
    return myJavaThreadState;
  }

  public @NlsSafe String getThreadStateDetail() {
    if (myOperation != null) {
      return myOperation.toString();
    }
    return myThreadStateDetail;
  }

  public boolean isEmptyStackTrace() {
    return myEmptyStackTrace;
  }

  public @NlsSafe String getExtraState() {
    return myExtraState;
  }

  public void setExtraState(final String extraState) {
    myExtraState = extraState;
  }

  public boolean isSleeping() {
    return "sleeping".equals(getThreadStateDetail()) ||
           (("parking".equals(getThreadStateDetail()) || "waiting on condition".equals(myState)) && isThreadPoolExecutor());
  }

  private boolean isThreadPoolExecutor() {
    return myStackTrace.contains("java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take") ||
           myStackTrace.contains("java.util.concurrent.ThreadPoolExecutor.getTask");
  }

  public boolean isAwaitedBy(ThreadState thread) {
    return myThreadsWaitingForMyLock.contains(thread);
  }

  public void addWaitingThread(@NotNull ThreadState thread) {
    myThreadsWaitingForMyLock.add(thread);
  }

  public boolean isDeadlocked() {
    return !myDeadlockedThreads.isEmpty();
  }

  public void addDeadlockedThread(ThreadState thread) {
    myDeadlockedThreads.add(thread);
  }

  public @Nullable ThreadOperation getOperation() {
    return myOperation;
  }

  public void setOperation(final @Nullable ThreadOperation operation) {
    myOperation = operation;
  }

  public boolean isWaiting() {
    return "on object monitor".equals(myThreadStateDetail) ||
           myState.startsWith("waiting") ||
           ("parking".equals(myThreadStateDetail) && !isSleeping());
  }

  public boolean isEDT() {
    final String name = getName();
    return isEDT(name);
  }

  public String getOwnableSynchronizers() {
    return ownableSynchronizers;
  }

  public void setOwnableSynchronizers(String ownableSynchronizers) {
    this.ownableSynchronizers = ownableSynchronizers;
  }

  public static boolean isEDT(String name) {
    return ThreadDumper.isEDT(name);
  }

  public boolean isDaemon() {
    return isDaemon;
  }

  public void setDaemon(boolean daemon) {
    isDaemon = daemon;
  }

  public static class CompoundThreadState extends ThreadState {
    private final ThreadState myOriginalState;
    private int myCounter = 1;

    public CompoundThreadState(ThreadState state) {
      super(state.myName, state.myState);
      myOriginalState = state;
    }

    public boolean add(ThreadState state) {
      if (myOriginalState.isEDT()) return false;
      if (!Objects.equals(state.myState, myOriginalState.myState)) return false;
      if (state.myEmptyStackTrace != myOriginalState.myEmptyStackTrace) return false;
      if (state.isDaemon != myOriginalState.isDaemon) return false;
      if (!Objects.equals(state.myJavaThreadState, myOriginalState.myJavaThreadState)) return false;
      if (!Objects.equals(state.myThreadStateDetail, myOriginalState.myThreadStateDetail)) return false;
      if (!Objects.equals(state.myExtraState, myOriginalState.myExtraState)) return false;
      if (!Comparing.haveEqualElements(state.myThreadsWaitingForMyLock, myOriginalState.myThreadsWaitingForMyLock)) return false;
      if (!Comparing.haveEqualElements(state.myDeadlockedThreads, myOriginalState.myDeadlockedThreads)) return false;
      if (!Objects.equals(getMergeableStackTrace(state.myStackTrace, true), getMergeableStackTrace(myOriginalState.myStackTrace, true))) return false;
      myCounter++;
      return true;
    }

    private static String getMergeableStackTrace(String stackTrace, boolean skipFirstLine) {
      if (stackTrace == null) return null;
      StringBuilder builder = new StringBuilder();
      String[] lines = stackTrace.split("\n");
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        if (i == 0 && skipFirstLine) continue;//first line has unique details
        line = line.replaceAll("<0x.+>\\s", "<merged>");
        builder.append(line).append("\n");
      }
      return builder.toString();
    }

    @Override
    public String getName() {
      return (myCounter == 1) ? myOriginalState.getName() : myCounter + " similar threads";
    }

    @Override
    public String getState() {
      return myOriginalState.getState();
    }

    @Override
    public String getStackTrace() {
      return myCounter == 1 ? myOriginalState.getStackTrace() : getMergeableStackTrace(myOriginalState.getStackTrace(), false);
    }

    @Override
    public Collection<ThreadState> getAwaitingThreads() {
      return myOriginalState.getAwaitingThreads();
    }

    @Override
    public String getJavaThreadState() {
      return myOriginalState.getJavaThreadState();
    }

    @Override
    public String getThreadStateDetail() {
      return myOriginalState.getThreadStateDetail();
    }

    @Override
    public boolean isEmptyStackTrace() {
      return myOriginalState.isEmptyStackTrace();
    }

    @Override
    public String getExtraState() {
      return myOriginalState.getExtraState();
    }

    @Override
    public boolean isAwaitedBy(ThreadState thread) {
      return myOriginalState.isAwaitedBy(thread);
    }

    @Override
    public boolean isDeadlocked() {
      return myOriginalState.isDeadlocked();
    }

    @Override
    public @Nullable ThreadOperation getOperation() {
      return myOriginalState.getOperation();
    }

    @Override
    public boolean isWaiting() {
      return myOriginalState.isWaiting();
    }

    @Override
    public boolean isEDT() {
      return myOriginalState.isEDT();
    }

    @Override
    public boolean isDaemon() {
      return myOriginalState.isDaemon();
    }

    @Override
    public boolean isSleeping() {
      return myOriginalState.isSleeping();
    }
  }
}
