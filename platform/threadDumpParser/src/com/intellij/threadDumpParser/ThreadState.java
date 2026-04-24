// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.threadDumpParser;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
  private boolean isVirtual;
  private Long uniqueId;
  private Long threadContainerUniqueId;
  private String type;
  private @NotNull Map<String, String> metadata = Collections.emptyMap();
  private final Set<ThreadState> myThreadsWaitingForMyLock = new HashSet<>();
  private final Set<ThreadState> myDeadlockedThreads = new HashSet<>();
  private String ownableSynchronizers;
  private String contendedMonitor;
  private final Map<Integer, List<String>> stackDepthToOwnedMonitors = new HashMap<>();

  private @Nullable ThreadOperation myOperation;
  private Boolean myKnownJDKThread;
  private int myStackDepth;

  public ThreadState(final String name, final String state) {
    // Loom's virtual threads often have empty name, try to make it look nicer.
    var explicitlyUnnamedName = "{unnamed}";
    if (name.isEmpty()) {
      myName = explicitlyUnnamedName;
    }
    else if (name.matches("@\\d+")) {
      myName = explicitlyUnnamedName + name;
    }
    else {
      myName = name;
    }
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

  public Collection<ThreadState> getDeadlockedThreads() {
    return Collections.unmodifiableSet(myDeadlockedThreads);
  }

  @Override
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

  public boolean isIdle() {
    return "idle".equals(myThreadStateDetail);
  }

  public String getOwnableSynchronizers() {
    return ownableSynchronizers;
  }

  public void setOwnableSynchronizers(String ownableSynchronizers) {
    this.ownableSynchronizers = ownableSynchronizers;
  }

  /**
   * Returns the monitor identifier this thread is waiting to acquire, or {@code null} if the thread is not
   * contending for any monitor.
   * <p>
   * Corresponds to {@code blockedOn} / {@code waitingOn} in jcmd JSON,
   * or {@link com.sun.jdi.ThreadReference#currentContendedMonitor()} in JDI.
   */
  public String getContendedMonitor() {
    return contendedMonitor;
  }

  public void setContendedMonitor(String contendedMonitor) {
    this.contendedMonitor = contendedMonitor;
  }

  /**
   * Records a monitor owned by this thread acquired at the given stack depth.
   * <p>
   * Monitors relinquished via {@code Object.wait()} should <b>not</b> be added here,
   * consistent with {@link com.sun.jdi.ThreadReference#ownedMonitors()}.
   * <p>
   * Use {@link addOwnedMonitor(String)}  if the stack depth is unknown.
   *
   * @param monitor    string representation of the monitor
   * @param stackDepth index of the stack frame where the monitor was acquired,
   */
  public void addOwnedMonitorAtDepth(@NotNull String monitor, int stackDepth) {
    int depth = stackDepth < 0 ? -1 : stackDepth;
    stackDepthToOwnedMonitors.computeIfAbsent(depth, k -> new ArrayList<>()).add(monitor);
  }

  /**
   * Records a monitor owned by this thread at an unknown stack depth.
   * Used when stack depth monitor info is not available.
   *
   * @see #addOwnedMonitorAtDepth(String, int)
   */
  public void addOwnedMonitor(@NotNull String monitor) {
    addOwnedMonitorAtDepth(monitor, -1);
  }

  /**
   * Returns the monitors owned by this thread at the given stack depth, or an empty list.
   */
  public List<String> getOwnedMonitorsAtDepth(int stackDepth) {
    return stackDepthToOwnedMonitors.getOrDefault(stackDepth, Collections.emptyList());
  }

  /**
   * Returns a set of all monitors owned by this thread.
   * <p>
   * Monitors relinquished via {@code Object.wait()} are <b>not</b> included,
   * consistent with {@link com.sun.jdi.ThreadReference#ownedMonitors()}.
   */
  public Set<String> getOwnedMonitors() {
    var result = new LinkedHashSet<String>();
    for (List<String> monitors : stackDepthToOwnedMonitors.values()) {
      result.addAll(monitors);
    }
    return Collections.unmodifiableSet(result);
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

  public boolean isVirtual() {
    return isVirtual;
  }

  public void setVirtual(boolean virtual) {
    isVirtual = virtual;
  }

  public @Nullable Long getUniqueId() { return uniqueId; }

  public void setUniqueId(@Nullable Long id) {
    uniqueId = id;
  }

  public Long getThreadContainerUniqueId() { return threadContainerUniqueId; }

  public void setThreadContainerUniqueId(Long id) {
    threadContainerUniqueId = id;
  }

  public @Nullable String getType() {
    return type;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  public @NotNull Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(@Nullable Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      this.metadata = Collections.emptyMap();
      return;
    }

    Map<String, String> copy = new HashMap<>(metadata);
    this.metadata = Collections.unmodifiableMap(copy);
  }
}
