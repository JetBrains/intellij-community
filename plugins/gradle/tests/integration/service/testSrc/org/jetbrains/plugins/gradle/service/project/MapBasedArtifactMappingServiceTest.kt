// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test


class MapBasedArtifactMappingServiceTest {

  @Test
  fun `check module id mapping is stored`() {
    val service = MapBasedArtifactMappingService()
    val path = "/my/path"
    val moduleId1 = "module-id-1"
    val moduleId2 = "module-id-2"

    service.storeModuleId(path, moduleId1)
    service.storeModuleId(path, moduleId2)

    val moduleMapping = service.getModuleMapping(path)
    assertThat(moduleMapping?.moduleIds).containsExactly(moduleId1, moduleId2)
    assertThat(moduleMapping?.hasNonModulesContent).isFalse()
    assertThat(moduleMapping?.ownerId).isEqualTo(OWNER_BASE_GRADLE)
  }

  @Test
  fun `check module id ownership is preserved`() {
    val service = MapBasedArtifactMappingService()
    val path = "/my/path"
    val ownerId = "test-owner"
    val moduleId1 = "module-id-1"
    val moduleId2 = "module-id-2"

    service.storeModuleId(path, moduleId1, ownerId)
    service.storeModuleId(path, moduleId2, "some-other-owner")

    val moduleMapping = service.getModuleMapping(path)

    assertThat(moduleMapping?.moduleIds).containsExactly(moduleId1, moduleId2)
    assertThat(moduleMapping?.ownerId).isEqualTo(ownerId)
  }

  @Test
  fun `check module id ownership is preserved when importing the mapping`() {
    val service = MapBasedArtifactMappingService()
    val path = "/my/path"
    val ownerId = "test-owner"
    val moduleId1 = "module-id-1"
    val moduleId2 = "module-id-2"

    service.storeModuleId(path, moduleId1, ownerId)
    service.storeModuleId(path, moduleId2, "some-other-owner")

    val copy = MapBasedArtifactMappingService()
    copy.putAll(service)
    val moduleMapping = copy.getModuleMapping(path)

    assertThat(moduleMapping?.moduleIds).containsExactly(moduleId1, moduleId2)
    assertThat(moduleMapping?.ownerId).isEqualTo(ownerId)
  }

  @Test
  fun `check Non-Modules-Content flag is preserved when importing the mapping`() {
    val service = MapBasedArtifactMappingService()
    val path = "/my/path"
    val moduleId1 = "module-id-1"

    service.storeModuleId(path, moduleId1)
    service.markArtifactPath(path, true)

    val copy = MapBasedArtifactMappingService()
    copy.putAll(service)
    val moduleMapping = copy.getModuleMapping(path)

    assertThat(moduleMapping?.moduleIds).containsExactly(moduleId1)
    assertThat(moduleMapping?.hasNonModulesContent).isTrue()
  }
}