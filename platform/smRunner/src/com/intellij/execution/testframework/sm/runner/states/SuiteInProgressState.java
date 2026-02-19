// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SuiteInProgressState extends TestInProgressState {
  private final SMTestProxy mySuiteProxy;
  private boolean myDefectFound = false;

  public SuiteInProgressState(final @NotNull SMTestProxy suiteProxy) {
    mySuiteProxy = suiteProxy;
  }

  /**
   * If any of child failed proxy also is defect
   */
  @Override
  public boolean isDefect() {
    if (myDefectFound) {
      return true;
    }

     //Test suit fails if any of its tests fails
    final List<? extends SMTestProxy> children = new ArrayList<SMTestProxy>(mySuiteProxy.getChildren());
    for (SMTestProxy child : children) {
      if (child.isDefect()) {
        myDefectFound = true;
        return true;
      }
    }

    //cannot cache because one of child tests may fail in future
    return false;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "SUITE PROGRESS";
  }
}
