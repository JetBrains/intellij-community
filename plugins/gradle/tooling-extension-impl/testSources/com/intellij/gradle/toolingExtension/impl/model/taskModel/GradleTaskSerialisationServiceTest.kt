// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializer
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.model.DefaultExternalTask
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.junit.jupiter.api.Test

class GradleTaskSerialisationServiceTest {

  @Test
  fun `serializes empty task model`() {
    val model = DefaultGradleTaskModel()

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(model), GradleTaskModel::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(model)
  }

  @Test
  fun `serializes task model`() {
    val buildTask = DefaultExternalTask().also {
      it.name = "build"
      it.qName = ":build"
      it.description = "Assembles and tests this project"
      it.group = "build"
      it.type = "org.gradle.api.DefaultTask"
      it.isJvm = false
      it.isTest = false
      it.isJvmTest = false
      it.isInherited = false
    }
    val testTask = DefaultExternalTask().also {
      it.name = "test"
      it.qName = ":test"
      it.description = "Runs unit tests"
      it.group = "verification"
      it.type = "org.gradle.api.tasks.testing.Test"
      it.isJvm = true
      it.isTest = true
      it.isJvmTest = true
      it.isInherited = false
    }
    val inheritedTask = DefaultExternalTask().also {
      it.name = "jar"
      it.qName = ":child:jar"
      it.description = null
      it.group = "build"
      it.type = "org.gradle.jvm.tasks.Jar"
      it.isJvm = true
      it.isTest = false
      it.isJvmTest = false
      it.isInherited = true
    }
    val model = DefaultGradleTaskModel().also {
      it.setTasks(linkedMapOf(
        "build" to buildTask,
        "test" to testTask,
        "jar" to inheritedTask,
      ))
    }

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(model), GradleTaskModel::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(model)
  }
}
