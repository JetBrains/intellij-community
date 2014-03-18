package com.intellij.remote;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public interface RemoteConnector {
  @Nullable
  String getId();

  @NotNull
  String getName();

  @NotNull
  RemoteConnectionType getType();

  void produceRemoteCredentials(Consumer<RemoteCredentials> remoteCredentialsConsumer);

  /**
   * Used to select different credentials. This method should be fast.
   * @return
   */
  @NotNull
  Object getConnectorKey();
}
