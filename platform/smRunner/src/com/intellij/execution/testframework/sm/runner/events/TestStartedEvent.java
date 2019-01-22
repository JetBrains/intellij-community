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

import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestStartedEvent extends BaseStartedNodeEvent {

  private boolean myConfig;

  /**
   * Will be removed in 2020
   *
   * @deprecated use {@link #TestStartedEvent(TestStarted)}
   * or {@link #TestStartedEvent(StartNodeEventInfo, String, String, boolean)}
   */
  @SuppressWarnings("unused") //Backward compatibility
  @Deprecated
  public TestStartedEvent(@NotNull TestStarted testStarted,
                          @Nullable String locationUrl) {
    this(testStarted);
  }

  /**
   * Will be removed in 2020
   *
   * @deprecated use {@link #TestStartedEvent(TestStarted)}
   * or {@link #TestStartedEvent(StartNodeEventInfo, String, String, boolean)}
   */
  @SuppressWarnings("unused") //Backward compatibility
  @Deprecated
  public TestStartedEvent(@NotNull TestStarted testStarted,
                          @Nullable String locationUrl,
                          @Nullable String metainfo) {
    this(testStarted);
  }

  public TestStartedEvent(@NotNull final TestStarted testStarted) {
    super(StartNodeEventInfoKt.getStartNodeInfo(testStarted, testStarted.getTestName()), testStarted);
  }

  /**
   * Will be removed in 2020
   *
   * @deprecated use {@link #TestStartedEvent(TestStarted)}
   * or {@link #TestStartedEvent(StartNodeEventInfo, String, String, boolean)}
   */
  public TestStartedEvent(@Nullable String name,
                          @Nullable String id,
                          @Nullable String parentId,
                          @Nullable String locationUrl,
                          @Nullable String metainfo,
                          @Nullable String nodeType,
                          @Nullable String nodeArgs,
                          boolean running) {
    this(new StartNodeEventInfo(name, id, parentId, locationUrl, metainfo), nodeType, nodeArgs, running);
  }

  public TestStartedEvent(@NotNull final StartNodeEventInfo info,
                          @Nullable String nodeType,
                          @Nullable String nodeArgs,
                          boolean running) {
    super(info,
          nodeType,
          nodeArgs,
          running);
  }

  public TestStartedEvent(@NotNull String name,
                          @Nullable String locationUrl) {
    this(name, locationUrl, null);
  }

  public TestStartedEvent(@NotNull String name,
                          @Nullable String locationUrl,
                          @Nullable String metainfo) {
    super(name, null, null, locationUrl, metainfo, null, null, true);
  }

  public void setConfig(boolean config) {
    myConfig = config;
  }

  public boolean isConfig() {
    return myConfig;
  }
}
