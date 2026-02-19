// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NotNullLazyKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TreeModelBuilderKeys {
  /**
   * Node grouping forms hierarchical structure.
   * For example, one module may have multiple content roots - and these roots may belong to different git repositories.
   * In this case, root caching should be performed at the particular repository node instead of a subtreeRoot
   * (this way each repository node will get its own module group node inside).
   *
   *
   * Prefer using [BaseChangesGroupingPolicy] methods or implementing [SimpleChangesGroupingPolicy] instead of using it directly.
   */
  @JvmField
  val IS_CACHING_ROOT: Key<Boolean> = Key.create("ChangesTree.IsCachingRoot")

  @JvmField
  val DIRECTORY_CACHE: NotNullLazyKey<MutableMap<String, ChangesBrowserNode<*>>, ChangesBrowserNode<*>> = NotNullLazyKey.createLazyKey("ChangesTree.DirectoryCache") { mutableMapOf() }
}