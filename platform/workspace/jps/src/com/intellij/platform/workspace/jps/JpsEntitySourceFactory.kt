// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps

object JpsEntitySourceFactory {
  fun createJpsEntitySourceForProjectLibrary(configLocation: JpsProjectConfigLocation): JpsFileEntitySource {
    return createJpsEntitySource(configLocation, "libraries")
  }

  fun createJpsEntitySourceForArtifact(configLocation: JpsProjectConfigLocation): JpsFileEntitySource {
    return createJpsEntitySource(configLocation, "artifacts")
  }

  private fun createJpsEntitySource(configLocation: JpsProjectConfigLocation, directoryLocation: String) = when (configLocation) {
    is JpsProjectConfigLocation.DirectoryBased -> JpsProjectFileEntitySource.FileInDirectory(
      configLocation.ideaFolder.append(directoryLocation),
      configLocation)
    is JpsProjectConfigLocation.FileBased -> JpsProjectFileEntitySource.ExactFile(configLocation.iprFile, configLocation)
    else -> error("Unexpected state")
  }
}