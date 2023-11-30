@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.project.Project

import serviceDeclarations.KtRegisteredProjectService


fun foo9(project: Project) {
  val service = <caret>KtRegisteredProjectService.getInstance(project)
}