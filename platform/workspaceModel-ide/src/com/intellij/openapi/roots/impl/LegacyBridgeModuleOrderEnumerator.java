package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ModuleRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LegacyBridgeModuleOrderEnumerator extends ModuleOrderEnumerator {
  public LegacyBridgeModuleOrderEnumerator(@NotNull ModuleRootModel rootModel,
                                           @Nullable LegacyBridgeOrderRootsCache cache) {
    super(rootModel, cache);
  }
}
