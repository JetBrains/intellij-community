/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.events;

import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestSuiteFinishedEvent extends TreeNodeEvent {

  public TestSuiteFinishedEvent(@NotNull TestSuiteFinished suiteFinished) {
    this(suiteFinished.getSuiteName(), TreeNodeEvent.getNodeId(suiteFinished));
  }

  public TestSuiteFinishedEvent(@NotNull String name) {
    this(name, null);
  }

  public TestSuiteFinishedEvent(@Nullable String name, @Nullable String id) {
    super(name, id);
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
  }
}
