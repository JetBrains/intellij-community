package com.intellij.remoteServer.configuration;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.remoteServer.ServerType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A mutable, named configuration holder for a remote server connection.
 * <p>
 * A {@code RemoteServer} instance bundles together a user-visible name, a {@link ServerType}, and a live {@link ServerConfiguration}
 * object. Both the name and the configuration are mutable: they can be changed at any time through the IDE Settings UI or programmatically.
 * <p>
 * <b>Notification contract.</b> Whenever the name or the configuration of a server is changed, interested parties must be notified by
 * publishing {@link RemoteServerListener#serverChanged} to {@link RemoteServerListener#TOPIC} on the <em>application</em> message bus.
 * The notification should ideally be sent <em>after</em> all mutations for a given edit are complete, not after each field change,
 * because listeners typically inspect the full configuration (e.g., to decide which open projects are affected).
 *
 * @see RemoteServerListener
 * @see RemoteServersManager
 */
public interface RemoteServer<C extends ServerConfiguration> {
  @NotNull @NlsSafe String getName();

  @NotNull
  ServerType<C> getType();

  /**
   * Returns the live, mutable configuration object.
   * <p>
   * Mutating the returned object does not automatically notify listeners. Publish {@link RemoteServerListener#serverChanged} after all
   * changes are applied (see the class-level contract).
   */
  @NotNull
  C getConfiguration();

  /**
   * Updates the user-visible name of this server.
   */
  void setName(String name);

  @ApiStatus.Internal
  default @NotNull UUID getUniqueId() {
    return RemoteServersManager.getInstance().getId(this);
  }
}
