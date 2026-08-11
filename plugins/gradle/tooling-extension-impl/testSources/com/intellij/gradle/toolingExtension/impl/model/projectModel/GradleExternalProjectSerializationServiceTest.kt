// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.projectModel

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializer
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.model.DefaultExternalProject
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.junit.jupiter.api.Test
import java.io.File

class GradleExternalProjectSerializationServiceTest {

  @Test
  fun `serializes external project with empty models`() {
    val project = DefaultExternalProject().also {
      it.externalSystemId = "GRADLE"
      it.id = ":"
      it.path = ":"
      it.identityPath = ":"
      it.name = "root"
      it.qName = "root"
      it.description = null
      it.group = ""
      it.version = ""
      it.projectDir = File(".")
      it.buildDir = File("build")
      it.buildFile = File("build.gradle.kts")
    }

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(project), ExternalProject::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(project)
  }

  @Test
  fun `serializes external project`() {
    val childProject = DefaultExternalProject().also {
      it.externalSystemId = "GRADLE"
      it.id = ":child"
      it.path = ":child"
      it.identityPath = ":child"
      it.name = "child"
      it.qName = "root.child"
      it.description = "Child project"
      it.group = "org.example"
      it.version = "1.0"
      it.projectDir = File("projects/child")
      it.buildDir = File("projects/child/build")
      it.buildFile = File("projects/child/build.gradle.kts")
    }
    val project = DefaultExternalProject().also {
      it.externalSystemId = "GRADLE"
      it.id = ":"
      it.path = ":"
      it.identityPath = ":"
      it.name = "root"
      it.qName = "root"
      it.description = "Root project"
      it.group = "org.example"
      it.version = "1.0"
      it.projectDir = File("projects/root")
      it.buildDir = File("projects/root/build")
      it.buildFile = File("projects/root/build.gradle.kts")
      it.addChildProject(childProject)
    }

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(project), ExternalProject::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(project)
  }
}
