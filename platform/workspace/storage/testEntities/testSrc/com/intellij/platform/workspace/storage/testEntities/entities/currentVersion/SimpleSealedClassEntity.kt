// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Open

interface SimpleSealedClassEntity: WorkspaceEntity {
  val text: String
  val someData: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SimpleSealedClass
}

@Open
sealed class SimpleSealedClass {
  abstract val type: String

  data class FirstKeyPropDataClass(val text: String): SimpleSealedClass() {
    override val type: String
      get() = "first"
  }

  data class SecondKeyPropDataClass(val value: Int, val list: List<String>): SimpleSealedClass() { // Change is here, new property
    override val type: String
      get() = "second"
  }
}