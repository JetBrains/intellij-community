// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.states;

/**
 * @author Roman Chernyatchik
 */
public final class TestPassedState extends AbstractState {
  //This state is common for all instances and doesn't contains
  //instance-specific information
  public static final TestPassedState INSTANCE = new TestPassedState();

  private TestPassedState() {
  }

  @Override
  public boolean isInProgress() {
    return false;
  }

  @Override
  public boolean isDefect() {
    return false;
  }

  @Override
  public boolean wasLaunched() {
    return true;
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean wasTerminated() {
    return false;
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.PASSED_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "PASSED";
  }
}
