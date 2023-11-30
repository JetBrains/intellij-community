@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.project.Project

import serviceDeclarations.LightServiceProjectLevel


fun foo16(project: Project) {
  val service = <caret>LightServiceProjectLevel.getInstance(project)
}