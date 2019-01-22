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

  /**
   * Will be removed in 2020
   *
   * @deprecated use {@link #TestSuiteStartedEvent(TestSuiteStarted)}
   * or {@link #TestSuiteStartedEvent(StartNodeEventInfo, String, String, boolean)}
   */
  public TestSuiteStartedEvent(@Nullable String name,
                               @Nullable String id,
                               @Nullable String parentId,
                               @Nullable String locationUrl,
                               @Nullable String metainfo,
                               @Nullable String nodeType,
                               @Nullable String nodeArgs,
                               boolean running) {
    this(new StartNodeEventInfo(name, id, parentId, locationUrl, metainfo), nodeType, nodeArgs, running);
  }

  public TestSuiteStartedEvent(@NotNull final StartNodeEventInfo info,
                               @Nullable String nodeType,
                               @Nullable String nodeArgs,
                               boolean running) {
    super(info, nodeType, nodeArgs, running);
  }

  /**
   * Will be removed in 2020
   *
   * @deprecated use {@link #TestSuiteStartedEvent(TestSuiteStarted)}
   * or {@link #TestSuiteStartedEvent(StartNodeEventInfo, String, String, boolean)}
   */
  @SuppressWarnings("unused") // Backward compatibility
  @Deprecated
  public TestSuiteStartedEvent(@NotNull TestSuiteStarted suiteStarted,
                               @Nullable String locationUrl) {
    this(suiteStarted);
  }

  /**
   * Will be removed in 2020
   *
   * @deprecated use {@link #TestSuiteStartedEvent(TestSuiteStarted)}
   * or {@link #TestSuiteStartedEvent(StartNodeEventInfo, String, String, boolean)}
   */
  @SuppressWarnings("unused") // Backward compatibility
  @Deprecated
  public TestSuiteStartedEvent(@NotNull TestSuiteStarted suiteStarted,
                               @Nullable String locationUrl,
                               @Nullable String metainfo) {
    this(suiteStarted);
  }

  public TestSuiteStartedEvent(@NotNull final TestSuiteStarted suiteStarted) {
    super(StartNodeEventInfoKt.getStartNodeInfo(suiteStarted, suiteStarted.getSuiteName()), suiteStarted);
  }

  public TestSuiteStartedEvent(@NotNull String name,
                               @Nullable String locationUrl) {
    this(name, locationUrl, null);
  }

  public TestSuiteStartedEvent(@NotNull String name, @Nullable String locationUrl, @Nullable String metainfo) {
    super(name, null, null, locationUrl, metainfo, null, null, true);
  }
}
