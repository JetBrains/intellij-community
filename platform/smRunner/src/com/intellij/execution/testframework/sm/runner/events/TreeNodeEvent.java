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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TreeNodeEvent {
  @NonNls public static final String ROOT_NODE_ID = "0";
  @NonNls public static final String TEST_REPORTER_ATTACHED = "enteredTheMatrix";
  @NonNls public static final String SUITE_TREE_STARTED = "suiteTreeStarted";
  @NonNls public static final String SUITE_TREE_ENDED = "suiteTreeEnded";
  @NonNls public static final String SUITE_TREE_NODE = "suiteTreeNode";
  @NonNls public static final String BUILD_TREE_ENDED_NODE = "treeEnded";
  @NonNls public static final String ROOT_PRESENTATION = "rootName";
  @NonNls public static final String ATTR_KEY_STATUS = "status";
  @NonNls public static final String ATTR_VALUE_STATUS_ERROR = "ERROR";
  @NonNls public static final String ATTR_VALUE_STATUS_WARNING = "WARNING";
  @NonNls public static final String ATTR_KEY_TEXT = "text";
  @NonNls public static final String ATTR_KEY_TEXT_ATTRIBUTES = "textAttributes";
  @NonNls public static final String ATTR_KEY_ERROR_DETAILS = "errorDetails";
  @NonNls public static final String ATTR_KEY_EXPECTED_FILE_PATH = "expectedFile";
  @NonNls public static final String ATTR_KEY_ACTUAL_FILE_PATH = "actualFile";
  @NonNls public static final String CUSTOM_STATUS = "customProgressStatus";
  @NonNls public static final String ATTR_KEY_TEST_TYPE = "type";
  @NonNls public static final String ATTR_KEY_TESTS_CATEGORY = "testsCategory";
  @NonNls public static final String ATTR_VAL_TEST_STARTED = "testStarted";
  @NonNls public static final String ATTR_VAL_TEST_FINISHED = "testFinished";
  @NonNls public static final String ATTR_VAL_TEST_FAILED = "testFailed";
  @NonNls public static final String TESTING_STARTED = "testingStarted";
  @NonNls public static final String TESTING_FINISHED = "testingFinished";
  @NonNls public static final String KEY_TESTS_COUNT = "testCount";
  @NonNls public static final String ATTR_KEY_TEST_ERROR = "error";
  @NonNls public static final String ATTR_KEY_TEST_COUNT = "count";
  @NonNls public static final String ATTR_KEY_TEST_DURATION = "duration";
  @NonNls public static final String ATTR_KEY_TEST_OUTPUT_FILE = "outputFile";
  @NonNls public static final String ATTR_KEY_LOCATION_URL = "locationHint";
  @NonNls public static final String ATTR_KEY_STACKTRACE_DETAILS = "details";
  @NonNls public static final String ATTR_KEY_DIAGNOSTIC = "diagnosticInfo";
  @NonNls public static final String ATTR_KEY_CONFIG = "config";

  @NotNull
  private final NodeEventInfo myEventInfo;

  protected TreeNodeEvent(@Nullable String name, @Nullable String id) {
    this(new NodeEventInfo(name, id));
  }

  protected TreeNodeEvent(@NotNull final NodeEventInfo eventInfo) {
    myEventInfo = eventInfo;
  }

  protected void fail(@NotNull String message) {
    throw new IllegalStateException(message + ", " + toString());
  }

  @Nullable
  public final String getName() {
    return myEventInfo.getName();
  }

  /**
   * @return tree node id, or null if undefined
   */
  @Nullable
  public final String getId() {
    return myEventInfo.getId();
  }

  @Override
  public final String toString() {
    StringBuilder buf = new StringBuilder(getClass().getSimpleName() + "{");
    append(buf, "name", getName());
    append(buf, "id", getId());
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

  @Nullable
  public static String getNodeId(@NotNull ServiceMessage message) {
    return getNodeId(message, "nodeId");
  }

  @Nullable
  public static String getNodeId(@NotNull ServiceMessage message, String key) {
    return message.getAttributes().get(key);
  }
}
