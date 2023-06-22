// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests.impl

import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.tests.createBuilderFrom
import com.intellij.platform.workspace.storage.tests.createEmptyBuilder
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import junit.framework.Assert.assertTrue
import org.junit.Test

class ExternalEntityMappingImplTest {
  @Test
  fun `mapping mutability test`() {
    val initialBuilder = createEmptyBuilder()
    val sampleEntity = initialBuilder addEntity SampleEntity(false, "123", ArrayList(), HashMap(),
                                                             VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                                             SampleEntitySource("test"))
    val mutableMapping = initialBuilder.getMutableExternalMapping<Int>("test.my.mapping")
    mutableMapping.addMapping(sampleEntity, 1)

    val newBuilder = createBuilderFrom(initialBuilder)

    val anotherEntity = initialBuilder addEntity SampleEntity(false, "321", ArrayList(), HashMap(),
                                                              VirtualFileUrlManagerImpl().fromUrl("file:///tmp"),
                                                              SampleEntitySource("test"))
    mutableMapping.addMapping(anotherEntity, 2)

    val anotherMapping = newBuilder.getExternalMapping<Int>("test.my.mapping")
    assertTrue(anotherMapping.getEntities(2).isEmpty())
  }
}