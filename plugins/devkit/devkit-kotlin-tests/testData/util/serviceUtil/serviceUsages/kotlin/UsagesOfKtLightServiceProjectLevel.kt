@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.project.Project

import serviceDeclarations.KtLightServiceProjectLevel


fun foo5(project: Project) {
  val service = <caret>KtLightServiceProjectLevel.getInstance(project)
}