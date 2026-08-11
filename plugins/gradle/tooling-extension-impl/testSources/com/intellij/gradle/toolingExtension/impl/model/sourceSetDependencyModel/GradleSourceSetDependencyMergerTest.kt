// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel

import com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.COMPILE_SCOPE
import com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.PROVIDED_SCOPE
import com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.RUNTIME_SCOPE
import com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.enrichDependencies
import com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel.GradleSourceSetDependencyMerger.mergeDependencies
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion.Companion.assertCollectionOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import org.jetbrains.plugins.gradle.model.DefaultExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.DefaultExternalProjectDependency
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency
import org.jetbrains.plugins.gradle.model.ExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.model.FileCollectionDependency
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

class GradleSourceSetDependencyMergerTest {

  @Nested
  inner class EnrichDependencies {

    @Test
    fun `library dependency in classpath passes through unchanged`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = enrichDependencies(
        classpathDependencies = listOf(libraryDependency(libraryJar)),
        configurationDependencies = listOf(libraryDependency(libraryJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
        }
      }
    }

    @Test
    fun `file dependency in classpath replaced by matching library dependency from configuration`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = enrichDependencies(
        classpathDependencies = listOf(fileDependency(libraryJar)),
        configurationDependencies = listOf(libraryDependency(libraryJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
        }
      }
    }

    @Test
    fun `file dependency in classpath replaced by matching project dependency from configuration`() {
      val moduleJar = Path("build/module.jar")

      val dependencies = enrichDependencies(
        classpathDependencies = listOf(fileDependency(moduleJar)),
        configurationDependencies = listOf(projectDependency(moduleJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalProjectDependency::class.java, it)
          assertEqualsOrdered(dependency.projectDependencyArtifacts.toPaths(), listOf(moduleJar))
        }
      }
    }

    @Test
    fun `file dependency in classpath with no match in configuration passes through unchanged`() {
      val classesDirectory = Path("build/classes")

      val dependencies = enrichDependencies(
        classpathDependencies = listOf(fileDependency(classesDirectory, excludedFromIndexing = true)),
        configurationDependencies = emptyList(),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(FileCollectionDependency::class.java, it)
          assertEqualsOrdered(dependency.files.toPaths(), listOf(classesDirectory))
          assertTrue(dependency.isExcludedFromIndexing)
        }
      }
    }

    @Test
    fun `file dependency in classpath with partial configuration match - matched replaced, unmatched preserved`() {
      val knownJar = Path("known.jar")
      val unknownJar = Path("custom-lib.jar")

      val dependencies = enrichDependencies(
        classpathDependencies = listOf(fileDependency(knownJar, unknownJar, excludedFromIndexing = true)),
        configurationDependencies = listOf(libraryDependency(knownJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), knownJar)
        }
        assertElement {
          val dependency = assertInstanceOf(FileCollectionDependency::class.java, it)
          assertEqualsOrdered(dependency.files.toPaths(), listOf(unknownJar))
          assertEquals(listOf(unknownJar.toString()).toString(), dependency.name)
          assertTrue(dependency.isExcludedFromIndexing)
        }
      }
    }
  }

  @Nested
  inner class MergeDependencies {

    @Test
    fun `dependency present in both compile and runtime gets COMPILE scope`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = mergeDependencies(
        compileDependencies = listOf(libraryDependency(libraryJar)),
        runtimeDependencies = listOf(libraryDependency(libraryJar)),
        providedDependencies = emptyList(),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
          assertEquals(COMPILE_SCOPE, dependency.scope)
        }
      }
    }

    @Test
    fun `dependency present only in compile gets PROVIDED scope`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = mergeDependencies(
        compileDependencies = listOf(libraryDependency(libraryJar)),
        runtimeDependencies = emptyList(),
        providedDependencies = emptyList(),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
          assertEquals(PROVIDED_SCOPE, dependency.scope)
        }
      }
    }

    @Test
    fun `dependency present only in runtime gets RUNTIME scope`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = mergeDependencies(
        compileDependencies = emptyList(),
        runtimeDependencies = listOf(libraryDependency(libraryJar)),
        providedDependencies = emptyList(),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
          assertEquals(RUNTIME_SCOPE, dependency.scope)
        }
      }
    }

    @Test
    fun `provided dependency in compile and runtime overrides scope to PROVIDED`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = mergeDependencies(
        compileDependencies = listOf(libraryDependency(libraryJar)),
        runtimeDependencies = listOf(libraryDependency(libraryJar)),
        providedDependencies = listOf(libraryDependency(libraryJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
          assertEquals(PROVIDED_SCOPE, dependency.scope)
        }
      }
    }

    @Test
    fun `provided dependency in compile-only overrides scope to PROVIDED`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = mergeDependencies(
        compileDependencies = listOf(libraryDependency(libraryJar)),
        runtimeDependencies = emptyList(),
        providedDependencies = listOf(libraryDependency(libraryJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
          assertEquals(PROVIDED_SCOPE, dependency.scope)
        }
      }
    }

    @Test
    fun `provided dependency in runtime-only overrides scope to PROVIDED`() {
      val libraryJar = Path("repo/library.jar")

      val dependencies = mergeDependencies(
        compileDependencies = emptyList(),
        runtimeDependencies = listOf(libraryDependency(libraryJar)),
        providedDependencies = listOf(libraryDependency(libraryJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), libraryJar)
          assertEquals(PROVIDED_SCOPE, dependency.scope)
        }
      }
    }

    @Test
    fun `provided dependency not in compile or runtime is added with PROVIDED scope`() {
      val compileJar = Path("repo/compile.jar")
      val providedJar = Path("repo/provided.jar")

      val dependencies = mergeDependencies(
        compileDependencies = listOf(libraryDependency(compileJar)),
        runtimeDependencies = listOf(libraryDependency(compileJar)),
        providedDependencies = listOf(libraryDependency(providedJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), compileJar)
          assertEquals(COMPILE_SCOPE, dependency.scope)
        }
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), providedJar)
          assertEquals(PROVIDED_SCOPE, dependency.scope)
        }
      }
    }

    @Test
    fun `dependencies are ordered - compile first, runtime-only second, extra provided last`() {
      val compileOnlyJar = Path("repo/compile-only.jar")
      val sharedJar = Path("repo/shared.jar")
      val runtimeOnlyJar = Path("repo/runtime-only.jar")
      val providedOnlyJar = Path("repo/provided-only.jar")

      val dependencies = mergeDependencies(
        compileDependencies = listOf(libraryDependency(compileOnlyJar), libraryDependency(sharedJar)),
        runtimeDependencies = listOf(libraryDependency(sharedJar), libraryDependency(runtimeOnlyJar)),
        providedDependencies = listOf(libraryDependency(providedOnlyJar)),
      )

      assertCollectionOrdered(dependencies) {
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), compileOnlyJar)
          assertEquals(PROVIDED_SCOPE, dependency.scope)
        }
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), sharedJar)
          assertEquals(COMPILE_SCOPE, dependency.scope)
        }
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), runtimeOnlyJar)
          assertEquals(RUNTIME_SCOPE, dependency.scope)
        }
        assertElement {
          val dependency = assertInstanceOf(ExternalLibraryDependency::class.java, it)
          assertEquals(dependency.file.toPath(), providedOnlyJar)
          assertEquals(PROVIDED_SCOPE, dependency.scope)
        }
      }
    }
  }

  companion object {

    @Suppress("IO_FILE_USAGE")
    private fun libraryDependency(path: Path) =
      DefaultExternalLibraryDependency().also {
        it.file = path.toFile()
      }

    private fun projectDependency(vararg artifacts: Path) =
      DefaultExternalProjectDependency().also {
        it.projectDependencyArtifacts = artifacts.map(Path::toFile)
      }

    private fun fileDependency(vararg paths: Path, excludedFromIndexing: Boolean = false) =
      DefaultFileCollectionDependency().also {
        it.files = paths.map(Path::toFile)
        it.isExcludedFromIndexing = excludedFromIndexing
      }

    private fun Collection<File>.toPaths(): List<Path> =
      map { it.toPath() }
  }
}
