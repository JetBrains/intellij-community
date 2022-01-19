// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface PersistentFsConnectionListener {
  ExtensionPointName<PersistentFsConnectionListener> EP_NAME = ExtensionPointName.create("com.intellij.persistentFsConnectionListener");

  default void connectionOpen() { }

  default void beforeConnectionClosed() { }
}
