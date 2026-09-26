// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public abstract class VcsCheckoutProcessor {

  public static final ExtensionPointName<VcsCheckoutProcessor> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.vcs.checkoutProcessor");

  public static VcsCheckoutProcessor getProcessor(final @NotNull @NonNls String protocol) {
    return EXTENSION_POINT_NAME.findFirstSafe(processor -> protocol.equals(processor.getId()));
  }

  public abstract @NonNls @NotNull String getId();

  public abstract boolean checkout(@NotNull Map<String, String> parameters, @NotNull VirtualFile parentDirectory, @NotNull String directoryName);
}
