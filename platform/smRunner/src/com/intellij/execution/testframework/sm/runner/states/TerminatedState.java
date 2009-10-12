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
 */
public class TerminatedState extends AbstractState {
  public static final TerminatedState INSTANCE = new TerminatedState();

  protected TerminatedState() {
  }

  public boolean isInProgress() {
    return false;
  }

  public boolean isDefect() {
    return true;
  }

  public boolean wasLaunched() {
    return true;
  }

  public boolean isFinal() {
    return true;
  }

  public boolean wasTerminated() {
    return true;
  }

  public Magnitude getMagnitude() {
    return Magnitude.TERMINATED_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TERMINATED";
  }
}
