// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

/**
 * Enables cross plugin communications.
 * Plugins may publish and subscribe to the topic instance provided with this interface.
 *
 */
public interface PluginCommunicationsListener extends EventListener {
  Topic<PluginCommunicationsListener> TOPIC = Topic.create("Plugin Message Topic", PluginCommunicationsListener.class);

  void messageReceived(String pluginId, String messageType, String messagePayload);
}
