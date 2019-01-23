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

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseStartedNodeEvent extends TreeNodeEvent {
  @NotNull
  private final StartNodeEventInfo myStartNodeEventInfo;

  private final String myNodeType;
  private final String myNodeArgs;
  private final boolean myRunning;

  protected BaseStartedNodeEvent(@NotNull final StartNodeEventInfo startNodeEventInfo, @NotNull final ServiceMessage message) {
    this(startNodeEventInfo, getNodeType(message), getNodeArgs(message), isRunning(message));
  }

  protected BaseStartedNodeEvent(@NotNull final StartNodeEventInfo startNodeEventInfo,
                                 @Nullable final String nodeType,
                                 @Nullable final String nodeArgs,
                                 final boolean running) {
    super(startNodeEventInfo);
    myStartNodeEventInfo = startNodeEventInfo;
    myNodeType = nodeType;
    myNodeArgs = nodeArgs;
    myRunning = running;
  }

  protected BaseStartedNodeEvent(@Nullable String name,
                                 @Nullable String id,
                                 @Nullable String parentId,
                                 @Nullable final String locationUrl,
                                 @Nullable final String metainfo,
                                 @Nullable String nodeType,
                                 @Nullable String nodeArgs,
                                 boolean running) {
    this(new StartNodeEventInfo(name, id, parentId, locationUrl, metainfo), nodeType, nodeArgs, running);
  }

  /**
   * @return parent node id, or null if undefined
   */
  @Nullable
  public String getParentId() {
    return myStartNodeEventInfo.getParentId();
  }

  @Nullable
  public String getLocationUrl() {
    return myStartNodeEventInfo.getLocationUrl();
  }

  @Nullable
  public String getMetainfo() {
    return myStartNodeEventInfo.getMetainfo();
  }

  @Nullable
  public String getNodeType() {
    return myNodeType;
  }

  @Nullable
  public String getNodeArgs() {
    return myNodeArgs;
  }

  public boolean isRunning() {
    return myRunning;
  }

  @NotNull
  public final StartNodeEventInfo getStartNodeEventInfo() {
    return myStartNodeEventInfo;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "parentId", getParentId());
    append(buf, "locationUrl", getLocationUrl());
    append(buf, "metainfo", getMetainfo());
    append(buf, "running", myRunning);
  }

  @Nullable
  public static String getLocation(@NotNull ServiceMessage message) {
    return message.getAttributes().get(ATTR_KEY_LOCATION_URL);
  }

  @Nullable
  public static String getName(@NotNull ServiceMessage message) {
    return message.getAttributes().get("name");
  }

  @Nullable
  public static String getParentNodeId(@NotNull ServiceMessage message) {
    return TreeNodeEvent.getNodeId(message, "parentNodeId");
  }

  @Nullable
  public static String getNodeType(@NotNull ServiceMessage message) {
    return message.getAttributes().get("nodeType");
  }

  @Nullable
  public static String getMetainfo(@NotNull ServiceMessage message) {
    return message.getAttributes().get("metainfo");
  }

  @NotNull
  public static TestDurationStrategy getDurationStrategy(@NotNull final ServiceMessage message) {
    return TestDurationStrategyKt.getDurationStrategy(message.getAttributes().get("durationStrategy"));
  }

  @Nullable
  public static String getNodeArgs(@NotNull ServiceMessage message) {
    return message.getAttributes().get("nodeArgs");
  }

  public static boolean isRunning(@NotNull ServiceMessage message) {
    String runningStr = message.getAttributes().get("running");
    if (StringUtil.isEmpty(runningStr)) {
      // old behavior preserved
      return true;
    }
    return Boolean.parseBoolean(runningStr);
  }
}
