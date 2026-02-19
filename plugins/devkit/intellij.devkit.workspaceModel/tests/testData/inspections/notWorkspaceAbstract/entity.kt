package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.WorkspaceEntity

@Target(AnnotationTarget.CLASS)
annotation class Abstract

@Abstract
interface NotWorkspaceAbstract : WorkspaceEntity

interface <warning descr="Entities can only inherit '@Abstract' entities">SomeEntity</warning> : NotWorkspaceAbstract
  
@com.intellij.platform.workspace.storage.annotations.Abstract
interface WorkspaceAbstract : WorkspaceEntity

interface AnotherEntity : WorkspaceAbstract