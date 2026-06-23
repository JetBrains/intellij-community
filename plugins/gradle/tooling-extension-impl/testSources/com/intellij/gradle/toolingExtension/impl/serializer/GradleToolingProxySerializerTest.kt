// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.serializer

import com.intellij.gradle.toolingExtension.impl.serializer.GradleToolingProxySerializerFactory.getSerializer
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalBuildIdentifier
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalGradleEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalJavaEnvironment
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalGradleDslBaseScriptModel
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalGroovyDslBaseScriptModel
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.dsl.InternalKotlinDslBaseScriptModel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import java.io.File

internal class GradleToolingProxySerializerTest {

  @Test
  fun `test gradle build environment serde`() {
    val serializer = getSerializer(GradleToolingProxySerializerTest::class.java.classLoader)
    val buildEnvironment = InternalBuildEnvironment(
      { InternalBuildIdentifier(File("identifier")) },
      { InternalGradleEnvironment(File("userHome"), "gradleVersion") },
      { InternalJavaEnvironment(File("JavaHome"), listOf("hello", "jvm", "arguments")) },
      { "something" }
    )
    val bytes = serializer.serialize(buildEnvironment)
    val deserialized = serializer.deserialize(bytes)
    assertInstanceOf<InternalBuildEnvironment>(deserialized)
    assertThat(deserialized)
      .usingRecursiveComparison()
      .isEqualTo(buildEnvironment)
  }

  @Test
  fun `test gradle dsl buildscript model serde`() {
    val serializer = getSerializer(GradleToolingProxySerializerTest::class.java.classLoader)
    val model = InternalGradleDslBaseScriptModel(
      InternalGroovyDslBaseScriptModel(
        listOf(File("first"), File("second")),
        listOf("implicit one", "implicit two")
      ),
      InternalKotlinDslBaseScriptModel(
        listOf(File("script"), File("templates"), File("class"), File("path")),
        listOf(File("compile"), File("class"), File("path")),
        listOf("implicit one", "implicit two"),
        listOf("template one", "template two")
      )
    )
    val bytes = serializer.serialize(model)
    val deserialized = serializer.deserialize(bytes)
    assertInstanceOf<InternalGradleDslBaseScriptModel>(deserialized)
    assertThat(deserialized)
      .usingRecursiveComparison()
      .isEqualTo(model)
  }

  @Test
  fun `test unregistered object serde`() {
    val serializer = getSerializer(GradleToolingProxySerializerTest::class.java.classLoader)
    assertThrows<IllegalArgumentException>("Class is not registered: Object") {
      serializer.serialize(Any())
    }
    assertThrows<IllegalArgumentException>("Class is not registered: Dummy") {
      serializer.serialize(Dummy())
    }
    assertThrows<IllegalArgumentException>("Class is not registered: ArrayDeque") {
      serializer.serialize(ArrayDeque<Any>())
    }
  }

  private class Dummy
}
