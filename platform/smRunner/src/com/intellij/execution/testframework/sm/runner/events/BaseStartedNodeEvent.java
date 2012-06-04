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

import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Simonchik
 */
public abstract class BaseStartedNodeEvent extends TreeNodeEvent {

  private final int myParentId;
  private final String myLocationUrl;

  protected BaseStartedNodeEvent(@NotNull String name,
                                 int id,
                                 int parentId,
                                 @Nullable final String locationUrl) {
    super(name, id);
    myParentId = parentId;
    myLocationUrl = locationUrl;
    validate();
  }

  private void validate() {
    if (myParentId < -1) {
      fail("parentId should be greater than -2");
    }
    if (getId() == -1 ^ myParentId == -1) {
      fail("id and parentId should be -1 or non-negative");
    }
  }

  /**
   * @return parent node id (non-negative integer), or -1 if undefined
   */
  public int getParentId() {
    return myParentId;
  }

  @Nullable
  public String getLocationUrl() {
    return myLocationUrl;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "parentId", myParentId);
    append(buf, "locationUrl", myLocationUrl);
  }

  public static int getParentNodeId(@NotNull MessageWithAttributes message) {
    return TreeNodeEvent.getIntAttribute(message, "parentNodeId");
  }

}
