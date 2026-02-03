// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.testEntities.entities.ChainedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.CompositeChildAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.TreeEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyChainedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyCompositeChildAbstractEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyTreeEntity
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CircularDependencyProhibitedTest {
  @Test
  fun `add one-to-one entity as parent to itself`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity ChainedEntity("Data", MySource)

    assertThrows<IllegalStateException> {
      builder.modifyChainedEntity(entity) {
        this.parent = this
      }
    }
  }

  @Test
  fun `add one-to-one entity as child to itself`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity ChainedEntity("Data", MySource)

    assertThrows<IllegalStateException> {
      builder.modifyChainedEntity(entity) {
        this.child = this
      }
    }
  }

  @Test
  fun `add one-to-many entity as child to itself`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity TreeEntity("data", MySource)

    assertThrows<IllegalStateException> {
      builder.modifyTreeEntity(entity) {
        this.children = listOf(this)
      }
    }
  }

  @Test
  fun `add one-to-many entity as parent to itself`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity TreeEntity("data", MySource)

    assertThrows<IllegalStateException> {
      builder.modifyTreeEntity(entity) {
        this.parentEntity = this
      }
    }
  }

  @Test
  fun `add one-to-abstract-many entity as child to itself`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity CompositeChildAbstractEntity(MySource)

    assertThrows<IllegalStateException> {
      builder.modifyCompositeChildAbstractEntity(entity) {
        this.children = listOf(this)
      }
    }
  }

  @Test
  fun `add one-to-abstract-many entity as parent to itself`() {
    val builder = MutableEntityStorage.create()
    val entity = builder addEntity CompositeChildAbstractEntity(MySource)

    assertThrows<IllegalStateException> {
      builder.modifyCompositeChildAbstractEntity(entity) {
        this.parentInList = this
      }
    }
  }

  @Test
  @Disabled("Can't generate entities due to the issue in generator")
  fun `add one-to-abstract-one entity as child to itself`() {
    //val builder = MutableEntityStorage.create()
    //val entity = builder addEntity ChainOneToOneImpl("data", MySource)
    //
    //assertThrows<IllegalStateException> {
    //  builder.modifyEntity(entity) {
    //    this.child = entity
    //  }
    //}
  }

  @Test
  @Disabled("Can't generate entities due to the issue in generator")
  fun `add one-to-abstract-one entity as parent to itself`() {
    //val builder = MutableEntityStorage.create()
    //val entity = builder addEntity ChainOneToOneImpl("data", MySource)
    //
    //assertThrows<IllegalStateException> {
    //  builder.modifyEntity(entity) {
    //    this.parent = entity
    //  }
    //}
  }
}
