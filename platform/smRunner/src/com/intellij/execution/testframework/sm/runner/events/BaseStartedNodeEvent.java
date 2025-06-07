// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.events;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseStartedNodeEvent extends TreeNodeEvent {

  private final String myParentId;
  private final String myLocationUrl;
  private final String myMetainfo;
  private final String myNodeType;
  private final String myNodeArgs;
  private final boolean myRunning;

  protected BaseStartedNodeEvent(@Nullable String name,
                                 @Nullable String id,
                                 @Nullable String parentId,
                                 final @Nullable String locationUrl,
                                 final @Nullable String metainfo,
                                 @Nullable String nodeType,
                                 @Nullable String nodeArgs,
                                 boolean running) {
    super(name, id);
    myParentId = parentId;
    myLocationUrl = locationUrl;
    myMetainfo =  metainfo;
    myNodeType = nodeType;
    myNodeArgs = nodeArgs;
    myRunning = running;
  }

  /**
   * @return parent node id, or null if undefined
   */
  public @Nullable String getParentId() {
    return myParentId;
  }

  public @Nullable String getLocationUrl() {
    return myLocationUrl;
  }

  public @Nullable String getMetainfo() {
    return myMetainfo;
  }

  public @Nullable String getNodeType() {
    return myNodeType;
  }

  public @Nullable String getNodeArgs() {
    return myNodeArgs;
  }

  public boolean isRunning() {
    return myRunning;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "parentId", myParentId);
    append(buf, "locationUrl", myLocationUrl);
    append(buf, "metainfo", myMetainfo);
    append(buf, "running", myRunning);
  }

  public static @Nullable String getParentNodeId(@NotNull MessageWithAttributes message) {
    return TreeNodeEvent.getNodeId(message, "parentNodeId");
  }

  public static @Nullable String getNodeType(@NotNull MessageWithAttributes message) {
    return message.getAttributes().get("nodeType");
  }

  public static @Nullable String getMetainfo(@NotNull ServiceMessage message) {
    return message.getAttributes().get("metainfo");
  }

  public static @Nullable String getNodeArgs(@NotNull MessageWithAttributes message) {
    return message.getAttributes().get("nodeArgs");
  }

  public static boolean isRunning(@NotNull MessageWithAttributes message) {
    String runningStr = message.getAttributes().get("running");
    if (StringUtil.isEmpty(runningStr)) {
      // old behavior preserved
      return true;
    }
    return Boolean.parseBoolean(runningStr);
  }

}
