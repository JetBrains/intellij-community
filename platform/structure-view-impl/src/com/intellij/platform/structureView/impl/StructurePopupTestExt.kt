// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl

import com.intellij.openapi.Disposable
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise

@ApiStatus.Internal
interface StructurePopupTestExt: Disposable {
  @TestOnly
  @ApiStatus.Internal
  fun getSpeedSearch(): TreeSpeedSearch?

  @TestOnly
  @ApiStatus.Internal
  fun setSearchFilterForTests(filter: String?)

  @TestOnly
  @ApiStatus.Internal
  fun setTreeActionState(actionName: String, state: Boolean)

  @TestOnly
  @ApiStatus.Internal
  fun initUi()

  @TestOnly
  @ApiStatus.Internal
  fun getTree(): Tree

  /**
   * Rebuilds the popup tree and waits until the rebuild has finished.
   * Exposed as [Promise] so that callers in [com.intellij.platform.testFramework] need only see the interface
   * (and not the concrete popup classes, which may live in plugin-content classloaders that are not visible).
   */
  @TestOnly
  @ApiStatus.Internal
  fun rebuildAndUpdateAsync(): Promise<*>

  /**
   * Waits until any in-flight popup update has finished. May return an already-resolved promise.
   */
  @TestOnly
  @ApiStatus.Internal
  fun waitUpdateFinishedAsync(): Promise<*>

  /**
   * Selects the element under the editor caret in the popup tree.
   */
  @TestOnly
  @ApiStatus.Internal
  fun selectCurrentAsync(): Promise<*>
}