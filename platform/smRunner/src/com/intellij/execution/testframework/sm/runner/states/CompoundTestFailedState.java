/*
 * Copyright 2000-2015 JetBrains s.r.o.
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


import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompoundTestFailedState extends TestFailedState {
  
  final List<TestFailedState> myStates = new ArrayList<>();

  public CompoundTestFailedState(@Nullable String localizedMessage, @Nullable String stackTrace) {
    super(localizedMessage, stackTrace);
  }

  public void addFailure(TestFailedState state) {
    myStates.add(state);
  }

  @Override
  public void printOn(Printer printer) {
 
    for (TestFailedState state : myStates) {
      state.printOn(printer);
    }
  }

  @NotNull
  public List<DiffHyperlink> getHyperlinks() {
    return ContainerUtil.map(ContainerUtil.filter(myStates, state -> state instanceof TestComparisionFailedState),
                             state -> ((TestComparisionFailedState)state).getHyperlink());
  }
}
