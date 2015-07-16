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

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeNodeEvent {

  private final String myName;
  private final int myId;

  public TreeNodeEvent(@Nullable String name, int id) {
    myName = name;
    myId = id;
    validate();
  }

  private void validate() {
    if (myId < -1) {
      fail("id should be greater than -2");
    }
  }

  protected void fail(@NotNull String message) {
    throw new IllegalStateException(message + ", " + toString());
  }

  @Nullable
  public String getName() {
    return myName;
  }

  /**
   * @return tree node id (non-negative integer), or -1 if undefined
   */
  public int getId() {
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
        buffer.append(String.valueOf(value));
      }
      buffer.append(", ");
    }
  }

  public static int getNodeId(@NotNull ServiceMessage message) {
    return getIntAttribute(message, "nodeId");
  }

  public static int getIntAttribute(@NotNull ServiceMessage message, @NotNull String key) {
    String value = message.getAttributes().get(key);
    if (value == null) {
      return -1;
    }
    return Integer.parseInt(value);
  }

}
