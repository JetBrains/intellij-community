// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ChangesBrowserRootNode : ChangesBrowserNode<String>(ROOT_NODE_VALUE) {
  companion object {
    const val ROOT_NODE_VALUE: String = "root"
  }
}