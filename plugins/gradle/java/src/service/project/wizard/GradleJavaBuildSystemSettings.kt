// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project.wizard

import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaBuildSystemWithSettings
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.Key

class GradleJavaBuildSystemSettings {
  val propertyGraph: PropertyGraph = PropertyGraph()
  val buildSystemButtons: Lazy<List<JavaBuildSystemWithSettings<out Any?>>> = lazy {
    JavaBuildSystemType.EP_NAME.extensionList.map {
      JavaBuildSystemWithSettings(it)
    }
  }

  val buildSystemProperty: GraphProperty<JavaBuildSystemWithSettings<*>> = propertyGraph.graphProperty {
    buildSystemButtons.value.first()
  }
  var groupId: String = ""
  var artifactId: String = ""

  companion object {
    val KEY = Key.create<GradleJavaBuildSystemSettings>(GradleJavaBuildSystemSettings::class.java.name)
  }
}