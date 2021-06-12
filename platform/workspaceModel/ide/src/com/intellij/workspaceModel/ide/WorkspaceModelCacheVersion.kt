// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

/**
 * Interface to define a version for the structure of workspace model entities.
 * Please update version in case you change the structure of the entities.
 * This interface has a goal to reset saved workspace model cache in case the version of entities has changed.
 * Use workspaceModel.cache.version extension point to register this extension.
 */
interface WorkspaceModelCacheVersion {
  fun getId(): String
  fun getVersion(): String
}
