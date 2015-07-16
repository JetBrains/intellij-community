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

import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestSuiteStartedEvent extends BaseStartedNodeEvent {

  public TestSuiteStartedEvent(@NotNull TestSuiteStarted suiteStarted,
                               @Nullable String locationUrl) {
    super(suiteStarted.getSuiteName(),
          TreeNodeEvent.getNodeId(suiteStarted),
          getParentNodeId(suiteStarted),
          locationUrl,
          BaseStartedNodeEvent.getNodeType(suiteStarted),
          BaseStartedNodeEvent.getNodeArgs(suiteStarted),
          BaseStartedNodeEvent.isRunning(suiteStarted));
  }

  public TestSuiteStartedEvent(@NotNull String name, @Nullable String locationUrl) {
    super(name, -1, -1, locationUrl, null, null, true);
  }

}
