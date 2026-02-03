package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase

interface EntityWithFakeImplementation : WorkspaceEntity

internal class EntityWithFakeImplementationImpl() : EntityWithFakeImplementation

interface <warning descr="Obsolete entity implementation">EntityWithOboleteImplementation</warning> : WorkspaceEntity

@GeneratedCodeApiVersion(2)
internal class EntityWithOboleteImplementationImpl() : EntityWithOboleteImplementation, WorkspaceEntityBase()

interface EntityWithCorrectImplementation : WorkspaceEntity

@GeneratedCodeApiVersion(3)
internal class EntityWithCorrectImplementationImpl() : EntityWithCorrectImplementation, WorkspaceEntityBase()

@Abstract
interface AbstractEntity : WorkspaceEntity

interface <warning descr="Obsolete entity implementation">AnotherEntityWithOboleteImplementation</warning> : AbstractEntity

@GeneratedCodeApiVersion(2)
internal class AnotherEntityWithOboleteImplementationImpl() : AnotherEntityWithOboleteImplementation, WorkspaceEntityBase()

interface AnotherEntityWithCorrectImplementation : WorkspaceEntity

@GeneratedCodeApiVersion(3)
internal class AnotherEntityWithCorrectImplementationImpl() : AnotherEntityWithCorrectImplementation, WorkspaceEntityBase()