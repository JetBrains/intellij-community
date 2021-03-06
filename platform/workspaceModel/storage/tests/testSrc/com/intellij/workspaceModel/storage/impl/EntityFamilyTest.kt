// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.entities.SampleEntity
import com.intellij.workspaceModel.storage.entities.SampleEntityData
import org.junit.Test

class EntityFamilyTest {
  @Test(expected = AssertionError::class)
  fun `remove and replace`() {
    // Initialize family
    val family: MutableEntityFamily<SampleEntity> = MutableEntityFamily.createEmptyMutable()
    val existing = SampleEntityData()
    family.add(existing)

    // Remove existing entity data
    family.remove(existing.id)

    // Try to replace missing entity data
    val replaceBy = SampleEntityData()
    replaceBy.id = existing.id
    family.replaceById(replaceBy)
  }
}
