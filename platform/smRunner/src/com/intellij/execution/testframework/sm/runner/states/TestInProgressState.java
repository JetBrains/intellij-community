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
 * @author Roman Chernyatchik
 *
 * Indicates that test is running.
 */
public class TestInProgressState extends AbstractState {
  //This state is common for all instances and doesn't contains
  //instance-specific information
  public static final TestInProgressState TEST = new TestInProgressState();

  protected TestInProgressState() {
  }

  @Override
  public boolean isInProgress() {
    return true;
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
    return false;
  }

  @Override
  public boolean wasTerminated() {
    return false;
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.RUNNING_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST PROGRESS";
  }
}
