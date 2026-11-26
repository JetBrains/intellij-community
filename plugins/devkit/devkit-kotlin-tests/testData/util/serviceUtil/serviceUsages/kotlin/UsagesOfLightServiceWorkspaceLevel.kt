@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.workspace.Workspace

import serviceDeclarations.LightServiceWorkspaceLevel


fun foo22(workspace: Workspace) {
  val service = <caret>LightServiceWorkspaceLevel.getInstance(workspace)
}