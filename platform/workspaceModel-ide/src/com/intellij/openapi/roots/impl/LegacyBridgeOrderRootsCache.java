package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

public class LegacyBridgeOrderRootsCache extends OrderRootsCache {
  public LegacyBridgeOrderRootsCache(@NotNull Disposable parentDisposable) {
    super(parentDisposable);
  }

  @Override
  public void clearCache() {
    super.clearCache();
  }
}
