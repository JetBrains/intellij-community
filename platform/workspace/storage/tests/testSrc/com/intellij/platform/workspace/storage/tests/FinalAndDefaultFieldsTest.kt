// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.AnotherDataClass
import com.intellij.platform.workspace.storage.testEntities.entities.FinalFieldsEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.modifyFinalFieldsEntity
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class FinalAndDefaultFieldsTest {
  @Test
  fun `check final and default fields initialized`() {
    val anotherVersion = 123
    val defaultDescription = "Default description"
    val builder = createEmptyBuilder()
    val anotherClass = AnotherDataClass("Foo", 1, true, url = "/user/opt/app/a.txt", displayName = "new revision")
    val finalFieldsEntity = FinalFieldsEntity(anotherClass, MySource) {
      assertEquals(0, this.anotherVersion)
      assertEquals(defaultDescription, this.description)
      this.anotherVersion = anotherVersion
    }

    assertEquals(anotherVersion, finalFieldsEntity.anotherVersion)
    assertEquals(defaultDescription, finalFieldsEntity.description)

    builder.addEntity(finalFieldsEntity)
    val snapshot = builder.toSnapshot()
    val entityFromStore = snapshot.entities(FinalFieldsEntity::class.java).single()
    assertTrue(entityFromStore.isEditable())
    assertEquals(anotherClass.url, entityFromStore.gitUrl)
    assertEquals(anotherClass.displayName, entityFromStore.displayName)
    assertEquals(anotherClass.revision, entityFromStore.gitRevision)
    assertEquals(anotherVersion, entityFromStore.anotherVersion)
    assertEquals(defaultDescription, entityFromStore.description)

    snapshot.toBuilder().modifyFinalFieldsEntity(entityFromStore) {
      assertEquals(anotherVersion, entityFromStore.anotherVersion)
      assertEquals(defaultDescription, entityFromStore.description)
    }
  }
}