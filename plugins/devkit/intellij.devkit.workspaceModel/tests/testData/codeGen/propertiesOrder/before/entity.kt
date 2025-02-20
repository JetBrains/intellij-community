// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract

interface SimpleEntity : WorkspaceEntityWithSymbolicId {
  override val symbolicId: SimpleId
    get() = SimpleId(name)

  val name: String
}

data class SimpleId(val name: String) : SymbolicEntityId<SimpleEntity> {
  override val presentableName: String
    get() = name
}

// partial copy of org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
@Abstract
interface BaseEntity : WorkspaceEntity {
  val name: String
  val moduleId: SimpleId
  
  val aBaseEntityProperty: String
  val dBaseEntityProperty: String
  val bBaseEntityProperty: String

  val sealedDataClassProperty: BaseDataClass
}

interface ChildEntity : BaseEntity {
  val cChildEntityProperty: String
}

sealed class BaseDataClass(val baseConstructorProperty: String) {
  open val baseBodyProperty: Int = 15
  val anotherBaseBodyProperty: Int = 16
}

sealed class DerivedDataClass(
  baseConstructorPropertyValue: String, 
  val derivedConstructorProperty: String
) : BaseDataClass(baseConstructorPropertyValue) {
  val derivedBodyProperty: Int = 23
}

class DerivedDerivedDataClass(
  baseConstructorPropertyValue: String, 
  override val baseBodyProperty: Int,
  derivedConstructorPropertyValue: String, 
  val derivedDerivedConstructorProperty: String
) : DerivedDataClass(baseConstructorPropertyValue, derivedConstructorPropertyValue) {
  val deriveDerivedBodyProperty: Int = 42
}
