// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.modules.toolwindow

import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import java.nio.file.Path

/**
 * NOTE: In case of externally stored project configuration (not under .idea directory)
 * make sure, that System property "external.project.config" has been set to point to .../system/projects/your_project/external_build_system
 */
fun loadJpsModel(projectPath: Path): JpsModel {
  val model = JpsElementFactory.getInstance().createModel() ?: throw Exception("Couldn't create JpsModel")
  val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)

  val m2HomePath = File(SystemProperties.getUserHome())
    .resolve(".m2")
    .resolve("repository")
  pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", m2HomePath.canonicalPath)

  val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
  JpsProjectLoader.loadProject(model.project, pathVariables, projectPath)

  return model
}