// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class AuxiliaryArtifactResolverImplTest {

  @field:TempDir
  lateinit var tempDir: File

  @Test
  fun `classify accepts public artifact identifier implementations`() {
    val componentIdentifier = proxy<ModuleComponentIdentifier> { method ->
      error("Unexpected call to ${method.name}")
    }
    val artifactIdentifier = proxy<ComponentArtifactIdentifier> { method ->
      when (method.name) {
        "getComponentIdentifier" -> componentIdentifier
        "getDisplayName" -> "generated sources"
        else -> error("Unexpected call to ${method.name}")
      }
    }
    val sources = File(tempDir, "library-sources.jar")
    val artifact = proxy<ResolvedArtifactResult> { method ->
      when (method.name) {
        "getId" -> artifactIdentifier
        "getFile" -> sources
        else -> error("Unexpected call to ${method.name}")
      }
    }

    assertThat(AuxiliaryArtifactResolverImpl.classify(setOf(artifact)))
      .containsEntry(componentIdentifier, setOf(sources))
  }

  @Suppress("UNCHECKED_CAST")
  private inline fun <reified T> proxy(crossinline invocation: (Method) -> Any?): T {
    return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, arguments ->
      when (method.name) {
        "equals" -> proxy === arguments?.single()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> T::class.java.simpleName
        else -> invocation(method)
      }
    } as T
  }
}
