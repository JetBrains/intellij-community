// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.ToString

interface WithCustomToStringEntity : WorkspaceEntity {
  val myName: String
  val version: Int

  @ToString
  val stringRepresentation: String
    get() = "Custom entity toString: $myName & $version"
}