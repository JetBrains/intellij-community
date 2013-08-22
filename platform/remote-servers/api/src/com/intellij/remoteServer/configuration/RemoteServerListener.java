package com.intellij.remoteServer.configuration;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface RemoteServerListener extends EventListener {
  Topic<RemoteServerListener> TOPIC = Topic.create("remote servers", RemoteServerListener.class);

  void serverAdded(@NotNull RemoteServer<?> server);
  void serverRemoved(@NotNull RemoteServer<?> server);
}
