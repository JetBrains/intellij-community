// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entity

import com.intellij.workspaceModel.storage.WorkspaceEntity

interface TestEntity: WorkspaceEntity {
  val name: String
  val count: Int
  val anotherField: One
}

data class One(val foo: String, val bar: String)