package org.jetbrains.io.webSocket;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;
import java.util.Map;

public interface WebSocketServerListener extends EventListener {
  void connected(@NotNull Client client, Map<String, List<String>> parameters);

  void disconnected(@NotNull Client client);
}
