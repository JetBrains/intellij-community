@file:Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")

import com.intellij.openapi.project.Project

import serviceDeclarations.LightServiceProjectLevelArray


fun foo17(project: Project) {
  val service = <caret>LightServiceProjectLevelArray.getInstance(project)
}