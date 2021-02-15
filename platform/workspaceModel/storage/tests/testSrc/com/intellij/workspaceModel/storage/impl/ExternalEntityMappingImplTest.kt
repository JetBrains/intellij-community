// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.addSampleEntity
import junit.framework.Assert.assertTrue
import org.junit.Test

class ExternalEntityMappingImplTest {
  @Test
  fun `mapping mutability test`() {
    val initialBuilder = WorkspaceEntityStorageBuilder.create()
    val sampleEntity = initialBuilder.addSampleEntity("123")
    val mutableMapping = initialBuilder.getMutableExternalMapping<Int>("MyMap")
    mutableMapping.addMapping(sampleEntity, 1)

    val newBuilder = WorkspaceEntityStorageBuilder.from(initialBuilder)

    val anotherEntity = initialBuilder.addSampleEntity("321")
    mutableMapping.addMapping(anotherEntity, 2)

    val anotherMapping = newBuilder.getExternalMapping<Int>("MyMap")
    assertTrue(anotherMapping.getEntities(2).isEmpty())
  }
}