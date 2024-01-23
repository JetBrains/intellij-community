// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.openapi.components.*
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
  for ((groupId, artifactId) in IntelliJPlatformProduct.values().asSequence().mapNotNull { product ->
    getMavenCoordinatesOfProduct(product)
  }) {
    // original coordinates of the product.
    result.add(groupId to artifactId)

    // coordinates used in the 'gradle-intellij-plugin' to specify IDE dependency
    result.add("com.jetbrains" to artifactId)
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

private fun getMavenCoordinatesOfProduct(product: IntelliJPlatformProduct): Pair<String, String>? {
  return when (product) {
    IntelliJPlatformProduct.IDEA -> "com.jetbrains.intellij.idea" to "ideaIU"
    IntelliJPlatformProduct.IDEA_IC -> "com.jetbrains.intellij.idea" to "ideaIC"
    IntelliJPlatformProduct.CLION -> "com.jetbrains.intellij.clion" to "clion"
    IntelliJPlatformProduct.PYCHARM -> "com.jetbrains.intellij.pycharm" to "pycharmPY"
    IntelliJPlatformProduct.PYCHARM_PC -> "com.jetbrains.intellij.pycharm" to "pycharmPC"
    IntelliJPlatformProduct.RIDER -> "com.jetbrains.intellij.rider" to "riderRD"
    IntelliJPlatformProduct.GOIDE -> "com.jetbrains.intellij.goland" to "goland"

    IntelliJPlatformProduct.RUBYMINE,
    IntelliJPlatformProduct.DATASPELL,
    IntelliJPlatformProduct.PYCHARM_EDU,
    IntelliJPlatformProduct.PHPSTORM,
    IntelliJPlatformProduct.WEBSTORM,
    IntelliJPlatformProduct.APPCODE,
    IntelliJPlatformProduct.MOBILE_IDE,
    IntelliJPlatformProduct.DBE,
    IntelliJPlatformProduct.ANDROID_STUDIO,
    IntelliJPlatformProduct.CWM_GUEST,
    IntelliJPlatformProduct.JETBRAINS_CLIENT,
    IntelliJPlatformProduct.GATEWAY,
    IntelliJPlatformProduct.IDEA_IE -> null
  }
}