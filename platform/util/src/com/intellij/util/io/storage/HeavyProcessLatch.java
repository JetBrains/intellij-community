// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.UtilBundle;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.EventListener;
import java.util.Iterator;
import java.util.List;

/**
 * Allows tracking some operations as "heavy" and querying their execution status.
 * Typically, some threads call {@link #performOperation} to execute heavy operation (heavy operations can be arbitrarily interleaved).
 * Some other threads then call {@link #isRunning()} and others to query for heavy operations running in a background.
 */
public final class HeavyProcessLatch {
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();

  private final List<Operation> myHeavyProcesses = ContainerUtil.createLockFreeCopyOnWriteList();
  private final EventDispatcher<HeavyProcessListener> myEventDispatcher = EventDispatcher.create(HeavyProcessListener.class);

  private HeavyProcessLatch() { }

  /**
   * Approximate type of heavy operation. Used in {@link com.intellij.codeInsight.daemon.impl.TrafficLightRenderer} UI as brief description.
   */
  public enum Type {
    Indexing("heavyProcess.type.indexing"),
    Syncing("heavyProcess.type.syncing"),
    Processing("heavyProcess.type.processing");

    private final String bundleKey;

    Type(String bundleKey) {
      this.bundleKey = bundleKey;
    }

    @Override
    public @Nls String toString() {
      return UtilBundle.message(bundleKey);
    }
  }

  /**
   * Executes {@code runnable} as a heavy operation. E.g., during this method execution, {@link #isRunning()} returns true.
   */
  public void performOperation(@NotNull Type type, @NotNull @Nls String displayName, @NotNull Runnable runnable) {
    Op op = new Op(type, displayName);
    myHeavyProcesses.add(op);
    myEventDispatcher.getMulticaster().processStarted(op);
    try {
      runnable.run();
    }
    finally {
      myHeavyProcesses.remove(op);
      myEventDispatcher.getMulticaster().processFinished(op);
    }
  }

  /**
   * @return {@code true} if some heavy operation is running on some thread
   */
  public boolean isRunning() {
    return !myHeavyProcesses.isEmpty();
  }

  /**
   * @return {@code true} if any heavy operation of type {@code type} is currently running in some thread
   */
  public boolean isRunning(@NotNull Type type) {
    return ContainerUtil.exists(myHeavyProcesses, op -> op.getType() == type);
  }

  /**
   * @return {@code true} if there is a heavy operation currently running in some thread,
   * which has its {@link Operation#getType()} != {@code type}
   */
  public boolean isRunningAnythingBut(@NotNull Type type) {
    return findRunningExcept(type) != null;
  }

  /**
   * @return heavy operation currently running, if any, in undefined order
   */
  @TestOnly
  public Operation getAnyRunningOperation() {
    Iterator<Operation> iterator = myHeavyProcesses.iterator();
    return iterator.hasNext() ? iterator.next() : null;
  }

  /**
   * @return a heavy operation currently running in some thread, which has its {@link Operation#getType()} != {@code type}
   */
  public @Nullable Operation findRunningExcept(@NotNull Type type) {
    for (Operation operation : myHeavyProcesses) {
      if (operation.getType() != type) {
        return operation;
      }
    }
    return null;
  }

  @SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
  public interface HeavyProcessListener extends EventListener {
    default void processStarted(@NotNull Operation op) { }

    void processFinished(@NotNull Operation op);
  }

  public interface Operation {
    @NotNull Type getType();
    @NotNull @Nls String getDisplayName();
  }

  public void addListener(@NotNull Disposable parentDisposable, @NotNull HeavyProcessListener listener) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  private static final class Op implements Operation {
    private final Type myType;
    private final @NotNull @Nls String myDisplayName;

    Op(@NotNull Type type, @NotNull @Nls String displayName) {
      myType = type;
      myDisplayName = displayName;
    }

    @Override
    public @NotNull Type getType() {
      return myType;
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
      return myDisplayName;
    }
  }
}
