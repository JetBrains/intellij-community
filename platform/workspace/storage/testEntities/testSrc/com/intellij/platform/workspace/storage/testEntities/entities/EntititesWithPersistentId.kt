// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*


interface FirstEntityWithPId : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: FirstPId
    get() {
      return FirstPId(data)
    }

}

data class FirstPId(override val presentableName: String) : SymbolicEntityId<FirstEntityWithPId>

interface SecondEntityWithPId : WorkspaceEntityWithSymbolicId {
  val data: String
  override val symbolicId: SecondPId
    get() = SecondPId(data)

}

data class SecondPId(override val presentableName: String) : SymbolicEntityId<SecondEntityWithPId>
data class TestPId(var presentableName: String, val aaa: Int?, var angry: Boolean)