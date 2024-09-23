// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

class ChangesBrowserRootNode : ChangesBrowserNode<String>(ROOT_NODE_VALUE) {
  companion object {
    const val ROOT_NODE_VALUE = "root"
  }
}