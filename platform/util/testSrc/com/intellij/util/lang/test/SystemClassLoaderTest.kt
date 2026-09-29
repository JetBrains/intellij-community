// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang.test

import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.ServiceLoader
import javax.tools.DocumentationTool
import javax.tools.JavaCompiler
import javax.tools.ToolProvider

class SystemClassLoaderTest {
  @Test
  fun `system compiler is available`() {
    assertThat(ToolProvider.getSystemJavaCompiler()).isNotNull()
  }

  @Test
  fun `system documentation tool is available`() {
    assertThat(ToolProvider.getSystemDocumentationTool()).isNotNull()
  }

  @ParameterizedTest
  @ValueSource(classes = [UrlClassLoader::class, PathClassLoader::class])
  fun `system loader constructor preserves JDK services`(loaderClass: Class<out ClassLoader>) {
    val parent = ModuleLayer.boot().findLoader("jdk.compiler")
    val classLoader = loaderClass.getConstructor(ClassLoader::class.java).newInstance(parent)

    assertThat(ServiceLoader.load(JavaCompiler::class.java, classLoader).map { it.javaClass.module.name }).contains("jdk.compiler")
    assertThat(ServiceLoader.load(DocumentationTool::class.java, classLoader).map { it.javaClass.module.name }).contains("jdk.javadoc")
  }
}
