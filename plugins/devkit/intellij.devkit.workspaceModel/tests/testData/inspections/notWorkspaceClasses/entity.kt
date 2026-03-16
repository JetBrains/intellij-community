package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase

interface WorkspaceEntity

interface NotWorkspaceEntity : WorkspaceEntity  {
  var property: String
}

interface AnotherNotWorkspaceEntity : WorkspaceEntity  {
  var flag: Boolean
}

interface EntitySource

object NotWorkspaceSource : EntitySource

class AnotherNotWorkspaceSource : EntitySource

interface NotWorkspaceMultipleInheritance : NotWorkspaceEntity, AnotherNotWorkspaceEntity, EntitySource

@Target(AnnotationTarget.CLASS)
annotation class GeneratedCodeApiVersion(val version: Int)

interface EntityWithOboleteImplementation : WorkspaceEntity

@GeneratedCodeApiVersion(2)
internal class EntityWithOboleteImplementationImpl() : EntityWithOboleteImplementation, WorkspaceEntityBase()