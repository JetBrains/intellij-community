package com.intellij.remoteServer.configuration;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for lifecycle and mutation events on {@link RemoteServer} instances.
 * <p>
 * Subscribe to {@link #TOPIC} on the <em>application</em> message bus to be notified when servers are added, removed, or changed.
 * <p>
 * {@link #serverChanged} covers both name and configuration changes. It is published once per logical "apply" — after all mutations for
 * a given edit are complete — rather than once per individual field change.
 */
public interface RemoteServerListener extends EventListener {
  Topic<RemoteServerListener> TOPIC = Topic.create("remote servers", RemoteServerListener.class);

  void serverAdded(@NotNull RemoteServer<?> server);
  void serverRemoved(@NotNull RemoteServer<?> server);
  default void serverChanged(@NotNull RemoteServer<?> server) {}
}
