@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.workspace.ProjectWorkspace

import serviceDeclarations.LightServiceWorkspaceLevel


fun foo22(workspace: ProjectWorkspace) {
  val service = <caret>LightServiceWorkspaceLevel.getInstance(workspace)
}