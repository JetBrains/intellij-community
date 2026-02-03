// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Open

interface SimpleObjectsEntity: WorkspaceEntity {
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleObjectsSealedClass
}

@Open
sealed class SimpleObjectsSealedClass {
  abstract val id: Int
  abstract val data: String

  object FirstSimpleObjectsSealedClassObject: SimpleObjectsSealedClass() {
    val value: Int = 5

    override val id: Int
      get() = 1
    override val data: String
      get() = "$value"
  }

  object SecondSimpleObjectsSealedClassObject: SimpleObjectsSealedClass() {
    val list: List<String> = listOf("some text", "something", "some data")
    val listSize: Int = list.size // Change is here, new property

    override val id: Int
      get() = 2
    override val data: String
      get() = "$list"
  }
}