// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class WorkspaceModelCacheInvalidationTest {

  private val testDisposable by disposableFixture()
  private val testRoot by tempPathFixture()

  private val Project.workspaceModelImpl: WorkspaceModelImpl
    get() = workspaceModel as WorkspaceModelImpl

  private val Project.workspaceModelCache: WorkspaceModelCache
    get() = requireNotNull(WorkspaceModelCache.getInstance(this)?.takeIf { it.enabled }) {
      "Workspace model cache MUST be enabled in this test"
    }

  @BeforeEach
  fun setUp() {
    WorkspaceModelCacheImpl.forceEnableCaching(testDisposable)
  }

  @Test
  fun `cache not loaded after invalidation, loaded after normal save`(): Unit = timeoutRunBlocking {
    // Open 1: save cache, then invalidate
    openProjectAsync(testRoot).useProjectAsync(save = true) { project ->
      project.workspaceModelCache.saveCacheNow()
      project.workspaceModelCache.invalidateCaches()
    }

    // Open 2: cache must not be loaded (was invalidated)
    openProjectAsync(testRoot).useProjectAsync(save = true) { project ->
      assertFalse(project.workspaceModelImpl.loadedFromCache) {
        "Workspace model cache must NOT be loaded after invalidation"
      }
      project.workspaceModelCache.saveCacheNow()
    }

    // Open 3: cache must be loaded (was saved normally in open 2)
    openProjectAsync(testRoot).useProjectAsync { project ->
      assertTrue(project.workspaceModelImpl.loadedFromCache) {
        "Workspace model cache MUST be loaded after invalidation"
      }
    }
  }
}
