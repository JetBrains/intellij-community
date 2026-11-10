// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.jetbrains.plugins.github.api.data.GHRepository
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GHPRSecurityServiceImplTest {
  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(names = ["ADMIN"])
  fun `test ADMIN is granted`(viewerPermission: GHRepositoryPermissionLevel) {
    assertTrue(createService(viewerPermission).currentUserHasPermissionLevel(GHRepositoryPermissionLevel.ADMIN))
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(names = ["READ", "TRIAGE", "TRIAGE_PLUS", "MODERATE", "WRITE", "MAINTAIN"])
  fun `test ADMIN is denied`(viewerPermission: GHRepositoryPermissionLevel) {
    assertFalse(createService(viewerPermission).currentUserHasPermissionLevel(GHRepositoryPermissionLevel.ADMIN))
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(names = ["WRITE", "MAINTAIN", "ADMIN"])
  fun `test WRITE is granted`(viewerPermission: GHRepositoryPermissionLevel) {
    assertTrue(createService(viewerPermission).currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE))
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(names = ["READ", "TRIAGE", "TRIAGE_PLUS", "MODERATE"])
  fun `test WRITE is denied`(viewerPermission: GHRepositoryPermissionLevel) {
    assertFalse(createService(viewerPermission).currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE))
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(names = ["TRIAGE", "TRIAGE_PLUS", "MODERATE", "WRITE", "MAINTAIN", "ADMIN"])
  fun `test TRIAGE is granted`(viewerPermission: GHRepositoryPermissionLevel) {
    assertTrue(createService(viewerPermission).currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE))
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(names = ["READ"])
  fun `test TRIAGE is denied`(viewerPermission: GHRepositoryPermissionLevel) {
    assertFalse(createService(viewerPermission).currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE))
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(GHRepositoryPermissionLevel::class)
  fun `test READ is granted`(viewerPermission: GHRepositoryPermissionLevel) {
    assertTrue(createService(viewerPermission).currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ))
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(names = ["READ", "TRIAGE", "WRITE", "ADMIN"])
  fun `test level is denied when the viewer permission is unknown`(level: GHRepositoryPermissionLevel) {
    assertFalse(createService(null).currentUserHasPermissionLevel(level))
  }

  private fun createService(viewerPermission: GHRepositoryPermissionLevel?): GHPRSecurityServiceImpl {
    val repo = mockk<GHRepository> {
      every { this@mockk.viewerPermission } returns viewerPermission
    }
    return GHPRSecurityServiceImpl(mockk(), mockk(), mockk(), mockk(), repo)
  }
}