// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.WorkspaceEntity

/**
 * Change is made in the [SpecialDataClass]
 */
interface ChangedPropertyDataClass : WorkspaceEntity {
  val text: String
  val propertyToChange: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.SpecialDataClass
}

data class SpecialDataClass(val text: List<String>)