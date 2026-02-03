package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase

interface EntityWithFakeImplementation : WorkspaceEntity {
  val property: String
  val isValid: Boolean
}

internal class <warning descr="Entity implementation has to be generated with the dedicated action">EntityWithFakeImplementationImpl</warning> : EntityWithFakeImplementation {
  override val property: String = ""
  override val isValid: Boolean = false
}

interface EntityWithImplementation : WorkspaceEntity {
  val property: String
  val isValid: Boolean
}

internal class EntityWithImplementationImpl() : EntityWithImplementation, WorkspaceEntityBase() {
  override val property: String = ""
  override val isValid: Boolean = false
}

interface OnlySource : EntitySource

object AnotherOnlySource : EntitySource

interface <warning descr="Cannot inherit EntitySource and WorkspaceEntity at the same time">EntityAndSource</warning> : WorkspaceEntity, EntitySource {
  val property: String
}

interface SomeEntity : WorkspaceEntity

interface <warning descr="Entities can only inherit '@Abstract' entities">InheritsNonAbstract</warning> : SomeEntity

<warning descr="Entities can only inherit '@Abstract' entities">interface</warning><error descr="Name expected"> </error>: SomeEntity

@Abstract
interface AbstractEntity1 : WorkspaceEntity

interface NonAbstractEntity1 : AbstractEntity1

@Abstract
interface AbstractEntity2 : WorkspaceEntity

interface NonAbstractEntity2 : AbstractEntity2

interface <warning descr="Multiple inheritance is not supported in workspace entities">NonAbstractEntity3</warning> : AbstractEntity1, AbstractEntity2

interface <warning descr="Entities can only inherit '@Abstract' entities">NonAbstractEntity4</warning> : AbstractEntity1, NonAbstractEntity2