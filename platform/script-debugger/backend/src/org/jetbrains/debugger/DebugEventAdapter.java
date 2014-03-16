package org.jetbrains.debugger;

import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DebugEventAdapter implements DebugEventListener {
  @Override
  public void suspended(@NotNull SuspendContext context) {
  }

  @Override
  public void resumed() {
  }

  @Override
  public void disconnected() {
  }

  @Override
  public void scriptLoaded(@NotNull Script script, @Nullable String sourceMapData) {
  }

  @Override
  public void scriptCollected(Script script) {
  }

  @Override
  public void scriptContentChanged(Script newScript) {
  }

  @Override
  public void scriptsCleared() {
  }

  @Override
  public void sourceMapFound(@NotNull Script script, @Nullable Url sourceMapUrl, @NotNull String sourceMapData) {
  }

  @Override
  public void navigated(String newUrl) {
  }
}
