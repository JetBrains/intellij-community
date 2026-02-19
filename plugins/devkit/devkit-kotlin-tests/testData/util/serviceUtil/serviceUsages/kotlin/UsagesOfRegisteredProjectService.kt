@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.project.Project

import serviceDeclarations.RegisteredProjectService


fun foo21(project: Project) {
  val service = <caret>RegisteredProjectService.getInstance(project)
}