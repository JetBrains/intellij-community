// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.AnotherDataClass
import com.intellij.workspaceModel.storage.entities.test.api.IgnoredFieldsEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import com.intellij.workspaceModel.storage.entities.unknowntypes.test.api.UnknownFieldEntity
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IgnoredFieldsTest {
  @Test
  fun `check ignored fields initialized`() {
    val builder = createEmptyBuilder()
    val anotherClass = AnotherDataClass("Foo", 1, true, url = "/user/opt/app/a.txt", displayName = "new revision")
    val ignoredFieldsEntity = IgnoredFieldsEntity(anotherClass, MySource)

    assertTrue(ignoredFieldsEntity.isEditable())
    assertEquals(anotherClass.url, ignoredFieldsEntity.gitUrl)
    assertEquals(anotherClass.displayName, ignoredFieldsEntity.displayName)
    assertEquals(anotherClass.revision, ignoredFieldsEntity.gitRevision)
    builder.addEntity(ignoredFieldsEntity)
    val snapshot = builder.toSnapshot()
    val entityFromStore = snapshot.entities(IgnoredFieldsEntity::class.java).single()
    assertTrue(entityFromStore.isEditable())
    assertEquals(anotherClass.url, entityFromStore.gitUrl)
    assertEquals(anotherClass.displayName, entityFromStore.displayName)
    assertEquals(anotherClass.revision, entityFromStore.gitRevision)

    snapshot.toBuilder().modifyEntity(entityFromStore) {
      assertTrue(isEditable())
      assertEquals(anotherClass.url, gitUrl)
      assertEquals(anotherClass.displayName, displayName)
      assertEquals(anotherClass.revision, gitRevision)
    }
  }
}