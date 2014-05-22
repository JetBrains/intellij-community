package org.jetbrains.debugger.connection;

import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.Disposable;
import com.intellij.util.io.socketConnection.ConnectionState;
import com.intellij.util.io.socketConnection.SocketConnectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BrowserConnection {
  @NotNull
  ConnectionState getState();

  void addListener(@NotNull SocketConnectionListener listener, @NotNull Disposable parentDisposable);

  void executeOnStart(@NotNull Runnable runnable);

  @Nullable
  WebBrowser getBrowser();
}
