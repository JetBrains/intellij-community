@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.workspace.Workspace

import serviceDeclarations.RegisteredWorkspaceService


fun foo23(workspace: Workspace) {
  val service = <caret>RegisteredWorkspaceService.getInstance(workspace)
}