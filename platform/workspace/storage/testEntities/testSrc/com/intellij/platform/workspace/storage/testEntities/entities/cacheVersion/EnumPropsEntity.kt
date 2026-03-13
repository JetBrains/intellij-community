// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

// In this test we can deserialize cache
interface EnumPropsEntity: WorkspaceEntity {
  val someEnum: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.EnumPropsEnum
}

enum class EnumPropsEnum(val value: Int) {
  FIRST(value = 5) {
    val text: String = "first"
  },

  SECOND(value = 10) {
    val list: List<String> = emptyList()
  },

  THIRD(value = 9) {
    val set: Set<Int> = setOf(1, 2, 3)
  }
}