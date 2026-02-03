package com.intellij.openapi.actionSystem.impl.segmentedActionBar;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

/**
 * This listener is called to force update UI in the new experimental toolbar,
 * for example, in case if some widget structure changed.
 */
@ApiStatus.Internal
public interface ToolbarActionsUpdatedListener {

  @Topic.AppLevel
  Topic<ToolbarActionsUpdatedListener> TOPIC =
    new Topic<>(ToolbarActionsUpdatedListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  void actionsUpdated();
}
