package com.intellij.openapi.actionSystem.impl.segmentedActionBar;

import com.intellij.util.messages.Topic;

public interface ToolbarActionsUpdatedListener {
  Topic<ToolbarActionsUpdatedListener> TOPIC =
    new Topic<>(ToolbarActionsUpdatedListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  void actionsUpdated();
}
