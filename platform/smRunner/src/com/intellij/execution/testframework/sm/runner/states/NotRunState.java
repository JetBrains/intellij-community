/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner.states;

/**
   * Default state for tests. Tes hasn't been started yet.
 */
public class NotRunState extends AbstractState {
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

  public boolean isInProgress() {
    return false;
  }

  //TODO[romeo] if hasn't run is it defect or not?   May be move it to settings
  public boolean isDefect() {
    return false;
  }

  public boolean wasLaunched() {
    return false;
  }

  public boolean isFinal() {
    return false;
  }

  public boolean wasTerminated() {
    return false;
  }

  public Magnitude getMagnitude() {
    return Magnitude.NOT_RUN_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "NOT RUN";
  }
}
