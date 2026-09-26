// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel

import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.provider.Providers
import org.gradle.api.tasks.SourceSetOutput
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable

class GradleSourceSetDependencyTraverserTest {

  @Test
  fun `traverse Configuration calls visitConfiguration`() {
    val configuration = configuration()
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), configuration, recordingVisitor)

    assertEquals(1, recordingVisitor.configurations.size)
    assertSame(configuration, recordingVisitor.configurations[0])
    assertTrue(recordingVisitor.sourceSetOutputs.isEmpty())
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  @Test
  fun `traverse SourceSetOutput calls visitSourceSetOutput`() {
    val sourceSetOutput = sourceSetOutput()
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), sourceSetOutput, recordingVisitor)

    assertEquals(1, recordingVisitor.sourceSetOutputs.size)
    assertSame(sourceSetOutput, recordingVisitor.sourceSetOutputs[0])
    assertTrue(recordingVisitor.configurations.isEmpty())
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  @Test
  fun `traverse FileCollection calls visitFileCollection`() {
    val fileCollection = fileCollection()
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), fileCollection, recordingVisitor)

    assertEquals(1, recordingVisitor.fileCollections.size)
    assertSame(fileCollection, recordingVisitor.fileCollections[0])
    assertTrue(recordingVisitor.configurations.isEmpty())
    assertTrue(recordingVisitor.sourceSetOutputs.isEmpty())
  }

  @Test
  fun `traverse Callable wrapping Configuration unwraps and calls visitConfiguration`() {
    val configuration = configuration()
    val callable = Callable { configuration }
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), callable, recordingVisitor)

    assertEquals(1, recordingVisitor.configurations.size)
    assertSame(configuration, recordingVisitor.configurations[0])
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  @Test
  fun `traverse Provider wrapping Configuration unwraps and calls visitConfiguration`() {
    val configuration = configuration()
    val provider = Providers.of(configuration)
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), provider, recordingVisitor)

    assertEquals(1, recordingVisitor.configurations.size)
    assertSame(configuration, recordingVisitor.configurations[0])
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  @Test
  fun `traverse ConfigurableFileCollection traverses its sources, not its files`() {
    // ConfigurableFileCollection extends FileCollection, which extends Iterable<File>.
    // Traverser must dispatch as ConfigurableFileCollection (call getFrom() and recurse), NOT as Iterable (iterate raw File objects).
    // This test fails if Iterable is checked before ConfigurableFileCollection in the dispatch chain.
    val configuration = configuration()
    val configurableFileCollection = configurableFileCollection(configuration)
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), configurableFileCollection, recordingVisitor)

    assertEquals(1, recordingVisitor.configurations.size)
    assertSame(configuration, recordingVisitor.configurations[0])
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  @Test
  fun `traverse FileSystemLocation calls visitFile`() {
    val fileSystemLocation = fileSystemLocation()
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), fileSystemLocation, recordingVisitor)

    assertEquals(1, recordingVisitor.fileSystemLocations.size)
    assertSame(fileSystemLocation, recordingVisitor.fileSystemLocations[0])
    assertTrue(recordingVisitor.configurations.isEmpty())
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  @Test
  fun `traverse Provider wrapping FileSystemLocation unwraps and calls visitFile`() {
    // Typical Gradle task-output pattern: someTask.outputFile (Provider<RegularFile>)
    // added to a ConfigurableFileCollection source. Traverser must unwrap and call visitFile.
    val fileSystemLocation = fileSystemLocation()
    val provider = Providers.of(fileSystemLocation)
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), provider, recordingVisitor)

    assertEquals(1, recordingVisitor.fileSystemLocations.size)
    assertSame(fileSystemLocation, recordingVisitor.fileSystemLocations[0])
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  @Test
  fun `traverse Iterable dispatches each element to the right visitor method`() {
    val configuration = configuration()
    val sourceSetOutput = sourceSetOutput()
    val recordingVisitor = RecordingVisitor()

    GradleSourceSetDependencyVisitor.traverse(context(), project(), listOf(configuration, sourceSetOutput), recordingVisitor)

    assertEquals(1, recordingVisitor.configurations.size)
    assertSame(configuration, recordingVisitor.configurations[0])
    assertEquals(1, recordingVisitor.sourceSetOutputs.size)
    assertSame(sourceSetOutput, recordingVisitor.sourceSetOutputs[0])
    assertTrue(recordingVisitor.fileCollections.isEmpty())
  }

  private fun context(): ModelBuilderContext = notImplemented(ModelBuilderContext::class.java)
  private fun project(): Project = notImplemented(Project::class.java)

  private fun configuration(): Configuration = notImplemented(Configuration::class.java)
  private fun sourceSetOutput(): SourceSetOutput = notImplemented(SourceSetOutput::class.java)
  private fun fileCollection(): FileCollection = notImplemented(FileCollection::class.java)
  private fun fileSystemLocation(): FileSystemLocation = notImplemented(FileSystemLocation::class.java)

  private fun configurableFileCollection(vararg sources: Any): ConfigurableFileCollection =
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    object : ConfigurableFileCollection by notImplemented(ConfigurableFileCollection::class.java) {
      override fun getFrom(): Set<Any> = setOf(*sources)
    }

  private class RecordingVisitor : GradleSourceSetDependencyVisitor {
    val configurations = mutableListOf<Configuration>()
    val sourceSetOutputs = mutableListOf<SourceSetOutput>()
    val fileCollections = mutableListOf<FileCollection>()
    val fileSystemLocations = mutableListOf<FileSystemLocation>()

    override fun visitConfiguration(configuration: Configuration) {
      configurations += configuration
    }

    override fun visitSourceSetOutput(sourceSetOutput: SourceSetOutput) {
      sourceSetOutputs += sourceSetOutput
    }

    override fun visitFileCollection(fileCollection: FileCollection) {
      fileCollections += fileCollection
    }

    override fun visitFile(file: FileSystemLocation) {
      fileSystemLocations += file
    }
  }
}
