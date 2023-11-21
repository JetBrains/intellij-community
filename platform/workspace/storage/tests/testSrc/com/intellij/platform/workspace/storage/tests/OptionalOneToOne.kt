// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OptionalOneToOneParentEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OptionalOneToOne {
  @Test
  fun `one and one`() {
    val builder = createEmptyBuilder()
    val parent = OptionalOneToOneParentEntity(MySource)
    builder.addEntity(parent)

    builder.addEntity(OptionalOneToOneChildEntity("One", MySource) {
      this.parent = parent
    })
    builder.addEntity(OptionalOneToOneChildEntity("Two", MySource) {
      this.parent = parent
    })

    assertNull(builder.entities(OptionalOneToOneChildEntity::class.java).single { it.data == "One" }.parent)
    assertNotNull(builder.entities(OptionalOneToOneChildEntity::class.java).single { it.data == "Two" }.parent)
    builder.assertConsistency()
  }
}