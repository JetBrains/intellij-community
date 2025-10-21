// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Open

// In this test we can deserialize cache
interface SubsetSealedClassEntity: WorkspaceEntity {
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SubsetSealedClass
}

@Open
sealed class SubsetSealedClass {
  abstract val name: String

  data class FirstSubsetSealedClassDataClass(override val name: String, val string: String): SubsetSealedClass()

  object FirstSubsetSealedClassObject: SubsetSealedClass() {
    override val name: String
      get() = "first object"
  }

  data class SecondSubsetSealedClassDataClass(override val name: String, val list: List<Int>): SubsetSealedClass()

  object SecondSubsetSealedClassObject: SubsetSealedClass() {
    override val name: String
      get() = "second object"
  }
}