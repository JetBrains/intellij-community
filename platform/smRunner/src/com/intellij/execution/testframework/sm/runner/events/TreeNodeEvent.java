// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.events;

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeNodeEvent {
  public static final @NonNls String ROOT_NODE_ID = "0";

  private final String myName;
  private final String myId;

  public TreeNodeEvent(@Nullable String name, @Nullable String id) {
    myName = name;
    myId = id;
  }

  protected void fail(@NotNull String message) {
    throw new IllegalStateException(message + ", " + toString());
  }

  public @Nullable String getName() {
    return myName;
  }

  /**
   * @return tree node id, or null if undefined
   */
  public @Nullable String getId() {
    return myId;
  }

  @Override
  public final String toString() {
    StringBuilder buf = new StringBuilder(getClass().getSimpleName() + "{");
    append(buf, "name", myName);
    append(buf, "id", myId);
    appendToStringInfo(buf);
    // drop last 2 chars: ', '
    buf.setLength(buf.length() - 2);
    buf.append("}");
    return buf.toString();
  }

  protected abstract void appendToStringInfo(@NotNull StringBuilder buf);

  protected static void append(@NotNull StringBuilder buffer,
                               @NotNull String key, @Nullable Object value) {
    if (value != null) {
      buffer.append(key).append("=");
      if (value instanceof String) {
        buffer.append("'").append(value).append("'");
      }
      else {
        buffer.append(value);
      }
      buffer.append(", ");
    }
  }

  public static @Nullable String getNodeId(@NotNull ServiceMessage message) {
    return getNodeId(message, "nodeId");
  }

  public static @Nullable String getNodeId(@NotNull ServiceMessage message, String key) {
    return message.getAttributes().get(key);
  }

}
