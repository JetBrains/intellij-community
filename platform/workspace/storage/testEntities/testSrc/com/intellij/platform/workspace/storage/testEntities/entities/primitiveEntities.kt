// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList


interface BooleanEntity : WorkspaceEntity {
  val data: Boolean

}

interface IntEntity : WorkspaceEntity {
  val data: Int

}

interface StringEntity : WorkspaceEntity {
  val data: String

}

interface ListEntity : WorkspaceEntity {
  val data: List<String>

}


interface OptionalIntEntity : WorkspaceEntity {
  val data: Int?

}


interface OptionalStringEntity : WorkspaceEntity {
  val data: String?

}

// Not supported at the moment
/*
interface OptionalListIntEntity : WorkspaceEntity {
  val data: List<Int>?
}
*/
