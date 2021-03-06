// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.states;

/**
   * Default state for tests. Tes hasn't been started yet.
 */
public final class NotRunState extends AbstractState {
  private static final NotRunState INSTANCE = new NotRunState();

  private NotRunState() {
  }

  /**
   * This state is common for all instances and doesn't contains
   * instance-specific information
   * @return
   */
  public static NotRunState getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isInProgress() {
    return false;
  }

  //TODO[romeo] if hasn't run is it defect or not?   May be move it to settings
  @Override
  public boolean isDefect() {
    return false;
  }

  @Override
  public boolean wasLaunched() {
    return false;
  }

  @Override
  public boolean isFinal() {
    return false;
  }

  @Override
  public boolean wasTerminated() {
    return false;
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.NOT_RUN_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "NOT RUN";
  }
}
