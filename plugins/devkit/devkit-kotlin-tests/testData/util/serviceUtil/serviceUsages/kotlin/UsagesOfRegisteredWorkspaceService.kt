@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.workspace.ProjectWorkspace

import serviceDeclarations.RegisteredWorkspaceService


fun foo23(workspace: ProjectWorkspace) {
  val service = <caret>RegisteredWorkspaceService.getInstance(workspace)
}