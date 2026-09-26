// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface ChangedComputablePropEntity : WorkspaceEntity {
  val text: String
  val computableProperty: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedDataClass
    get() = com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedDataClass(listOf(text, "more text", text))
}

data class ChangedDataClass(val texts: List<String>)