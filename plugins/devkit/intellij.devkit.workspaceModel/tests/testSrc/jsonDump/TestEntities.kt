// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.jsonDump

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

data class TestSymbolicId(val name: String) : SymbolicEntityId<BaseTestEntity> {
  override val presentableName: @NlsSafe String
    get() = "Name: $name"
}

sealed class AbstractClass {
  abstract val string: String
}

class ImplClass1(override val string: String, val version: Int) : AbstractClass()

class ImplClass2(override val string: String, val name: String) : AbstractClass()

interface BaseTestEntity : WorkspaceEntityWithSymbolicId {
  val name: String
  val children: List<ChildEntity>
  val singleChild: SingleChild?
  val listOfAbstract: List<AbstractClass>
  val stringList: List<String>
  val stringSet: Set<String>
  override val symbolicId: TestSymbolicId
    get() = TestSymbolicId(name)
}

interface ChildEntity : WorkspaceEntity {
  val childName: String
  @Parent
  val parent: BaseTestEntity
}

interface ExtensionChildEntity : WorkspaceEntity {
  val extensionChildName: String
  @Parent
  val parent: BaseTestEntity
  val listOfUrls: List<VirtualFileUrl>
}

val BaseTestEntity.extensionChildren: List<ExtensionChildEntity>
  by WorkspaceEntity.extension()

interface SingleChild : WorkspaceEntity {
  val someData: String
  @Parent
  val parent: BaseTestEntity
}