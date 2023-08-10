// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui.tree;

import com.intellij.remoteServer.runtime.ServerConnection;
import org.jetbrains.annotations.NotNull;

public interface ServerTreeNodeExpander {
  void expand(@NotNull ServerConnection<?> connection, @NotNull String deploymentName);
}
