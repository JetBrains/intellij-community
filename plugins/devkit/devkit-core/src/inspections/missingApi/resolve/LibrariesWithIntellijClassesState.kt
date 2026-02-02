// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct

/**
 * This is used to save "groupId:artifactId" pairs for all libraries that may contain IntelliJ classes.
 * External annotations `@ApiStatus.AvailableSince` should be attached to such libraries.
 *
 * Until it is possible to attach external annotations to arbitrary libraries (see https://youtrack.jetbrains.com/issue/IDEA-201991)
 * we need this workaround for Kotlin project, which has IDEA classes stored in dependencies like `kotlin.build:ideaIC:<version>`
 * and `kotlin.build:java:<version>`. Kotlin project may supplement the storage.xml with coordinates of all
 * libraries containing IntelliJ classes.
 */
@Service(Service.Level.PROJECT)
@State(name = "libraries-with-intellij-classes", storages = [Storage("libraries-with-intellij-classes.xml")])
internal class LibrariesWithIntellijClassesSetting : SimplePersistentStateComponent<LibrariesWithIntellijClassesState>(createDefaultState()) {
  companion object {
    fun getInstance(project: Project): LibrariesWithIntellijClassesSetting = project.service()
  }

  val intellijApiContainingLibraries: List<LibraryCoordinatesState>
    get() = state.intellijApiContainingLibraries
}

internal class LibrariesWithIntellijClassesState : BaseState() {
  val intellijApiContainingLibraries by list<LibraryCoordinatesState>()
}

internal class LibraryCoordinatesState : BaseState() {
  var groupId by string()
  var artifactId by string()
}

private fun getKnownIntellijLibrariesCoordinates(): List<Pair<String, String>> {
  val result = mutableListOf<Pair<String, String>>()

  IntelliJPlatformProduct.entries.forEach {
    it.mavenCoordinates?.split(":")?.let { (groupId,artifactId) ->
      // original coordinates of the product.
      result.add(groupId to artifactId)

      // coordinates used in the 'gradle-intellij-plugin' 1.x to specify IDE dependency; obsolete in IntelliJ Platform Gradle Plugin 2.x
      result.add("com.jetbrains" to artifactId)
    }
  }
  return result
}

private fun createDefaultState(): LibrariesWithIntellijClassesState {
  val result = LibrariesWithIntellijClassesState()
  getKnownIntellijLibrariesCoordinates()
    .mapTo(result.intellijApiContainingLibraries) { (groupId, artifactId) ->
      val state = LibraryCoordinatesState()
      state.groupId = groupId
      state.artifactId = artifactId
      state
    }
  return result
}
