// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializer
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.artifacts.Dependency
import org.jetbrains.plugins.gradle.model.DefaultExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.DefaultExternalMultiLibraryDependency
import org.jetbrains.plugins.gradle.model.DefaultExternalProjectDependency
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency
import org.jetbrains.plugins.gradle.model.DefaultUnresolvedExternalDependency
import org.jetbrains.plugins.gradle.model.GradleSourceSetDependencyModel
import org.junit.jupiter.api.Test
import java.io.File

class GradleSourceSetDependencySerialisationServiceTest {

  @Test
  fun `serializes empty source set dependency model`() {
    val model = DefaultGradleSourceSetDependencyModel()

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(model), GradleSourceSetDependencyModel::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(model)
  }

  @Test
  fun `serializes source set dependency model`() {
    val libraryNestedDependency = DefaultUnresolvedExternalDependency().also {
      it.group = "org.example"
      it.name = "libraryNested"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.classpathOrder = 101
      it.failureMessage = "Could not resolve org.example:libraryNested:1.0"
    }
    val libraryDependency = DefaultExternalLibraryDependency().also {
      it.group = "org.example"
      it.name = "library"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.selectionReason = "Selected by conflict resolution"
      it.classpathOrder = 1
      it.exported = false
      it.dependencies = listOf(libraryNestedDependency)
      it.file = File("repo/org/example/library/1.0/library-1.0.jar")
      it.source = File("repo/org/example/library/1.0/library-1.0-sources.jar")
      it.javadoc = File("repo/org/example/library/1.0/library-1.0-javadoc.jar")
    }

    val multiLibraryNestedDependency = DefaultUnresolvedExternalDependency().also {
      it.group = "org.example"
      it.name = "multiLibraryNested"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.classpathOrder = 102
      it.failureMessage = "Could not resolve org.example:multiLibraryNested:1.0"
    }
    val multiLibraryDependency = DefaultExternalMultiLibraryDependency().also {
      it.group = "org.example"
      it.name = "multiLibrary"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "RUNTIME"
      it.selectionReason = "Selected by attributes"
      it.classpathOrder = 2
      it.exported = true
      it.dependencies = listOf(multiLibraryNestedDependency)
      it.files.add(File("repo/org/example/multiLibrary/1.0/multiLibrary-1.0.jar"))
      it.files.add(File("repo/org/example/multiLibrary/1.0/multiLibrary-helper-1.0.jar"))
      it.sources.add(File("repo/org/example/multiLibrary/1.0/multiLibrary-1.0-sources.jar"))
      it.javadoc.add(File("repo/org/example/multiLibrary/1.0/multiLibrary-1.0-javadoc.jar"))
    }

    val projectNestedDependency = DefaultUnresolvedExternalDependency().also {
      it.group = "org.example"
      it.name = "projectNested"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.classpathOrder = 103
      it.failureMessage = "Could not resolve org.example:projectNested:1.0"
    }
    val projectDependency = DefaultExternalProjectDependency().also {
      it.group = "org.example"
      it.name = "project"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.selectionReason = "Selected project variant"
      it.classpathOrder = 3
      it.exported = false
      it.dependencies = listOf(projectNestedDependency)
      it.projectPath = ":project"
      it.configurationName = Dependency.DEFAULT_CONFIGURATION
      it.projectDependencyArtifacts = listOf(File("projects/project/build/libs/project.jar"))
      it.projectDependencyArtifactsSources = listOf(File("projects/project/build/libs/project-sources.jar"))
    }

    val fileCollectionNestedDependency = DefaultUnresolvedExternalDependency().also {
      it.group = "org.example"
      it.name = "fileCollectionNested"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.classpathOrder = 104
      it.failureMessage = "Could not resolve org.example:fileCollectionNested:1.0"
    }
    val fileCollectionDependency = DefaultFileCollectionDependency().also {
      it.group = "org.example"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "RUNTIME"
      it.selectionReason = "Collected from task output"
      it.classpathOrder = 4
      it.exported = true
      it.dependencies = listOf(fileCollectionNestedDependency)
      it.files = listOf(
        File("generated/fileCollection/classes"),
        File("generated/fileCollection/resources"),
      )
      it.isExcludedFromIndexing = true
    }

    val unresolvedNestedDependency = DefaultUnresolvedExternalDependency().also {
      it.group = "org.example"
      it.name = "unresolvedNested"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.classpathOrder = 105
      it.failureMessage = "Could not resolve org.example:unresolvedNested:1.0"
    }
    val unresolvedDependency = DefaultUnresolvedExternalDependency().also {
      it.group = "org.example"
      it.name = "unresolved"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "COMPILE"
      it.selectionReason = "Requested directly"
      it.classpathOrder = 5
      it.exported = false
      it.dependencies = listOf(unresolvedNestedDependency)
      it.failureMessage = "Could not resolve org.example:unresolved:1.0"
    }

    val testDependency = DefaultExternalProjectDependency().also {
      it.group = "org.example"
      it.name = "testProject"
      it.version = "1.0"
      it.packaging = "jar"
      it.scope = "TEST"
      it.classpathOrder = 10
      it.projectPath = ":testProject"
      it.configurationName = Dependency.DEFAULT_CONFIGURATION
      it.projectDependencyArtifacts = listOf(File("projects/testProject/build/libs/testProject.jar"))
      it.projectDependencyArtifactsSources = listOf(File("projects/testProject/build/libs/testProject-sources.jar"))
    }

    val model = DefaultGradleSourceSetDependencyModel().also { model ->
      model.dependencies = linkedMapOf(
        "main" to listOf(
          libraryDependency,
          multiLibraryDependency,
          projectDependency,
          fileCollectionDependency,
          unresolvedDependency,
        ),
        "test" to listOf(testDependency),
      )
    }

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(model), GradleSourceSetDependencyModel::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(model)
  }
}
