// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity

interface WithSealedEntity : WorkspaceEntity {
  val classes: List<MySealedClass>
  val interfaces: List<MySealedInterface>
}

sealed class MySealedClass

data class MySealedClassOne(val info: String) : MySealedClass()
data class MySealedClassTwo(val info: String) : MySealedClass()

sealed interface MySealedInterface

data class MySealedInterfaceOne(val info: String) : MySealedInterface
data class MySealedInterfaceTwo(val info: String) : MySealedInterface
