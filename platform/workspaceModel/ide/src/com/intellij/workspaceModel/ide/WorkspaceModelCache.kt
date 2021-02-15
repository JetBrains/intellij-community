// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

interface WorkspaceModelCache {

  /**
   * Returns true if there is a request for workspace model cache storing.
   * Resets to false after cache storing (via alarm or [saveCacheNow].
   */
  val isCachingRequested: Boolean

  /**
   * Save workspace model caches
   */
  fun saveCacheNow()
}
