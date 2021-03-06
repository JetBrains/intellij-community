// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
@State(name = "libraries-with-intellij-classes", storages = [Storage("libraries-with-intellij-classes.xml")])
class LibrariesWithIntellijClassesSetting : SimplePersistentStateComponent<LibrariesWithIntellijClassesState>(defaultState) {

  companion object {
    val defaultState = LibrariesWithIntellijClassesState().also {
      getKnownIntellijLibrariesCoordinates()
        .mapTo(it.intellijApiContainingLibraries) { (groupId, artifactId) ->
          val state = LibraryCoordinatesState()
          state.groupId = groupId
          state.artifactId = artifactId
          state
        }
    }

    fun getInstance(project: Project): LibrariesWithIntellijClassesSetting =
      ServiceManager.getService(project, LibrariesWithIntellijClassesSetting::class.java)
  }
}

class LibrariesWithIntellijClassesState : BaseState() {
  var intellijApiContainingLibraries by list<LibraryCoordinatesState>()
}

class LibraryCoordinatesState : BaseState() {
  var groupId by string()
  var artifactId by string()
}

private fun getKnownIntellijLibrariesCoordinates(): List<Pair<String, String>> {
  val result = arrayListOf<Pair<String, String>>()
  IntelliJPlatformProduct.values().mapNotNull { product ->
    getMavenCoordinatesOfProduct(product)
  }.forEach { (groupId, artifactId) ->
    //Original coordinates of the product.
    result += groupId to artifactId

    //Coordinates used in the 'gradle-intellij-plugin' to specify IDE dependency
    result += "com.jetbrains" to artifactId
  }
  return result
}

@Suppress("HardCodedStringLiteral")
private fun getMavenCoordinatesOfProduct(product: IntelliJPlatformProduct): Pair<String, String>? = when (product) {
  IntelliJPlatformProduct.IDEA -> "com.jetbrains.intellij.idea" to "ideaIU"
  IntelliJPlatformProduct.IDEA_IC -> "com.jetbrains.intellij.idea" to "ideaIC"
  IntelliJPlatformProduct.CLION -> "com.jetbrains.intellij.clion" to "clion"
  IntelliJPlatformProduct.PYCHARM -> "com.jetbrains.intellij.pycharm" to "pycharmPY"
  IntelliJPlatformProduct.PYCHARM_PC -> "com.jetbrains.intellij.pycharm" to "pycharmPC"
  IntelliJPlatformProduct.RIDER -> "com.jetbrains.intellij.rider" to "riderRD"
  IntelliJPlatformProduct.GOIDE -> "com.jetbrains.intellij.goland" to "goland"

  IntelliJPlatformProduct.RUBYMINE,
  IntelliJPlatformProduct.PYCHARM_DS,
  IntelliJPlatformProduct.PYCHARM_EDU,
  IntelliJPlatformProduct.PHPSTORM,
  IntelliJPlatformProduct.WEBSTORM,
  IntelliJPlatformProduct.APPCODE,
  IntelliJPlatformProduct.MOBILE_IDE,
  IntelliJPlatformProduct.DBE,
  IntelliJPlatformProduct.ANDROID_STUDIO,
  IntelliJPlatformProduct.CWM_GUEST,
  IntelliJPlatformProduct.IDEA_IE -> null
}