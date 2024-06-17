// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleDependencySerialisationUtil")

package com.intellij.gradle.toolingExtension.impl.model.dependencyModel

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*
import org.jetbrains.plugins.gradle.tooling.serialization.step
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap.ObjectFactory
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector
import java.io.IOException

@ApiStatus.Internal
class DependencyWriteContext {
  val dependencies: ObjectCollector<ExternalDependency, IOException> = ObjectCollector()
}

@ApiStatus.Internal
class DependencyReadContext {
  val dependencies = IntObjectMap<AbstractExternalDependency>()
}

@ApiStatus.Internal
fun writeDependency(
  writer: IonWriter,
  context: DependencyWriteContext,
  dependency: ExternalDependency,
) {
  context.dependencies.add(dependency) { isAdded, objectId ->
    writer.step(IonType.STRUCT) {
      writeInt(writer, OBJECT_ID_FIELD, objectId)
      if (isAdded) {
        writeString(writer, "_type", when (dependency) {
          is ExternalLibraryDependency -> ExternalLibraryDependency::class.java.simpleName
          is ExternalMultiLibraryDependency -> ExternalMultiLibraryDependency::class.java.simpleName
          is ExternalProjectDependency -> ExternalProjectDependency::class.java.simpleName
          is FileCollectionDependency -> FileCollectionDependency::class.java.simpleName
          is UnresolvedExternalDependency -> UnresolvedExternalDependency::class.java.simpleName
          else -> throw RuntimeException("Unsupported dependency type: " + dependency.javaClass.name)
        })
        writeDependencyCommonFields(writer, context, dependency)
        when (dependency) {
          is ExternalLibraryDependency -> writeLibraryDependencyFields(writer, dependency)
          is ExternalMultiLibraryDependency -> writeMultiLibraryDependencyFields(writer, dependency)
          is ExternalProjectDependency -> writeProjectDependencyFields(writer, dependency)
          is FileCollectionDependency -> writeFileCollectionDependencyFields(writer, dependency)
          is UnresolvedExternalDependency -> writeUnresolvedDependencyFields(writer, dependency)
          else -> throw RuntimeException("Unsupported dependency type: " + dependency.javaClass.name)
        }
      }
    }
  }
}

@ApiStatus.Internal
fun readDependency(
  reader: IonReader,
  context: DependencyReadContext,
): ExternalDependency? {
  if (reader.next() == null) return null
  return reader.step {
    val objectId = readInt(reader, OBJECT_ID_FIELD)
    context.dependencies.computeIfAbsent(objectId, object : ObjectFactory<AbstractExternalDependency> {
      override fun newInstance(): AbstractExternalDependency {
        return when (val type = readString(reader, "_type")!!) {
          ExternalLibraryDependency::class.java.simpleName -> DefaultExternalLibraryDependency()
          ExternalMultiLibraryDependency::class.java.simpleName -> DefaultExternalMultiLibraryDependency()
          ExternalProjectDependency::class.java.simpleName -> DefaultExternalProjectDependency()
          FileCollectionDependency::class.java.simpleName -> DefaultFileCollectionDependency()
          UnresolvedExternalDependency::class.java.simpleName -> DefaultUnresolvedExternalDependency()
          else -> throw RuntimeException("Unsupported dependency type: $type")
        }
      }

      override fun fill(dependency: AbstractExternalDependency) {
        fillDependencyCommonFields(reader, context, dependency)
        when (dependency) {
          is DefaultExternalLibraryDependency -> fillLibraryDependencyFields(reader, dependency)
          is DefaultExternalMultiLibraryDependency -> fillMultiLibraryDependencyFields(reader, dependency)
          is DefaultExternalProjectDependency -> fillProjectDependencyFields(reader, dependency)
          is DefaultFileCollectionDependency -> fillFileCollectionDependencyFields(reader, dependency)
          is DefaultUnresolvedExternalDependency -> fillUnresolvedDependencyFields(reader, dependency)
          else -> throw RuntimeException("Unsupported dependency type: " + dependency.javaClass.name)
        }
      }
    })
  }
}

private fun writeDependencyCommonFields(writer: IonWriter, context: DependencyWriteContext, dependency: ExternalDependency) {
  writeDependencyId(writer, dependency)
  writeString(writer, "scope", dependency.scope)
  @Suppress("DEPRECATION")
  writeString(writer, "selectionReason", dependency.selectionReason)
  writeInt(writer, "classpathOrder", dependency.classpathOrder)
  writeBoolean(writer, "exported", dependency.exported)
  writeCollection(writer, "dependencies", dependency.dependencies) {
    writeDependency(writer, context, it)
  }
}

private fun fillDependencyCommonFields(
  reader: IonReader,
  context: DependencyReadContext,
  dependency: AbstractExternalDependency,
) {
  fillDependencyId(reader, dependency.id as DefaultExternalDependencyId)
  dependency.scope = readString(reader, "scope")
  dependency.selectionReason = readString(reader, "selectionReason")
  dependency.classpathOrder = readInt(reader, "classpathOrder")
  dependency.exported = readBoolean(reader, "exported")
  dependency.dependencies = readList(reader, "dependencies") {
    readDependency(reader, context)
  }
}

private fun writeDependencyId(writer: IonWriter, dependency: ExternalDependency) {
  writer.setFieldName("id")
  writer.step(IonType.STRUCT) {
    val dependencyId = dependency.id
    writeString(writer, "group", dependencyId.group)
    writeString(writer, "name", dependencyId.name)
    writeString(writer, "version", dependencyId.version)
    writeString(writer, "packaging", dependencyId.packaging)
    writeString(writer, "classifier", dependencyId.classifier)
  }
}

private fun fillDependencyId(
  reader: IonReader,
  dependencyId: DefaultExternalDependencyId,
) {
  assertNotNull(reader.next())
  assertFieldName(reader, "id")
  reader.step {
    dependencyId.group = readString(reader, "group")
    dependencyId.name = readString(reader, "name")
    dependencyId.version = readString(reader, "version")
    dependencyId.packaging = readString(reader, "packaging")!!
    dependencyId.classifier = readString(reader, "classifier")
  }
}

private fun writeLibraryDependencyFields(writer: IonWriter, dependency: ExternalLibraryDependency) {
  writeFile(writer, "file", dependency.file)
  writeFile(writer, "source", dependency.source)
  writeFile(writer, "javadoc", dependency.javadoc)
}

private fun fillLibraryDependencyFields(reader: IonReader, dependency: DefaultExternalLibraryDependency) {
  dependency.file = readFile(reader, "file")
  dependency.source = readFile(reader, "source")
  dependency.javadoc = readFile(reader, "javadoc")
}

private fun writeMultiLibraryDependencyFields(writer: IonWriter, dependency: ExternalMultiLibraryDependency) {
  writeFiles(writer, "files", dependency.files)
  writeFiles(writer, "sources", dependency.sources)
  writeFiles(writer, "javadocs", dependency.javadoc)
}

private fun fillMultiLibraryDependencyFields(reader: IonReader, dependency: DefaultExternalMultiLibraryDependency) {
  dependency.files.addAll(readFileList(reader, null))
  dependency.sources.addAll(readFileList(reader, null))
  dependency.javadoc.addAll(readFileList(reader, null))
}

private fun writeProjectDependencyFields(writer: IonWriter, dependency: ExternalProjectDependency) {
  writeString(writer, "projectPath", dependency.projectPath)
  writeString(writer, "configurationName", dependency.configurationName)
  writeFiles(writer, "projectDependencyArtifacts", dependency.projectDependencyArtifacts)
  writeFiles(writer, "projectDependencyArtifactsSources", dependency.projectDependencyArtifactsSources)
}

private fun fillProjectDependencyFields(reader: IonReader, dependency: DefaultExternalProjectDependency) {
  dependency.projectPath = readString(reader, "projectPath")
  dependency.configurationName = readString(reader, "configurationName")
  dependency.projectDependencyArtifacts = readFileList(reader, null)
  dependency.projectDependencyArtifactsSources = readFileList(reader, null)
}

private fun writeFileCollectionDependencyFields(writer: IonWriter, dependency: FileCollectionDependency) {
  writeFiles(writer, "files", dependency.files)
  writeBoolean(writer, "excludedFromIndexing", dependency.isExcludedFromIndexing)
}

private fun fillFileCollectionDependencyFields(reader: IonReader, dependency: DefaultFileCollectionDependency) {
  dependency.files = readFileList(reader, null)
  dependency.isExcludedFromIndexing = readBoolean(reader, "excludedFromIndexing")
}

private fun writeUnresolvedDependencyFields(writer: IonWriter, dependency: UnresolvedExternalDependency) {
  writeString(writer, "failureMessage", dependency.failureMessage)
}

private fun fillUnresolvedDependencyFields(reader: IonReader, dependency: DefaultUnresolvedExternalDependency) {
  dependency.failureMessage = readString(reader, "failureMessage")
}