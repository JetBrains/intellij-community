// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.states;


import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class CompoundTestFailedState extends TestFailedState {
  
  final List<TestFailedState> myStates = ContainerUtil.createLockFreeCopyOnWriteList();

  public CompoundTestFailedState() {
    super(null, null);
  }

  public void addFailure(TestFailedState state) {
    myStates.add(state);
    Disposer.register(this, state);
  }

  @Override
  public void printOn(Printer printer) {
 
    for (TestFailedState state : myStates) {
      state.printOn(printer);
    }
  }

  public @NotNull @Unmodifiable List<DiffHyperlink> getHyperlinks() {
    return ContainerUtil.map(ContainerUtil.filter(myStates, state -> state instanceof TestComparisonFailedState),
                             state -> ((TestComparisonFailedState)state).getHyperlink());
  }
}
