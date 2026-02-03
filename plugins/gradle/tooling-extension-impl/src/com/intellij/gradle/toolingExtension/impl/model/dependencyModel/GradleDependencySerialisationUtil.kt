// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleDependencySerialisationUtil")

package com.intellij.gradle.toolingExtension.impl.model.dependencyModel

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.jetbrains.plugins.gradle.model.AbstractExternalDependency
import org.jetbrains.plugins.gradle.model.DefaultExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.DefaultExternalMultiLibraryDependency
import org.jetbrains.plugins.gradle.model.DefaultExternalProjectDependency
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency
import org.jetbrains.plugins.gradle.model.DefaultUnresolvedExternalDependency
import org.jetbrains.plugins.gradle.model.ExternalDependency
import org.jetbrains.plugins.gradle.model.ExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.ExternalMultiLibraryDependency
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.model.FileCollectionDependency
import org.jetbrains.plugins.gradle.model.UnresolvedExternalDependency
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.OBJECT_ID_FIELD
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.assertFieldName
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.assertNotNull
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readBoolean
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readFile
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readFileList
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readInt
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readList
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readString
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeBoolean
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeCollection
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeFile
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeFiles
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeInt
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeString
import org.jetbrains.plugins.gradle.tooling.serialization.step
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap.ObjectFactory
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector
import java.io.IOException

private const val DEPENDENCY_TYPE_FIELD = "_type"

private const val DEPENDENCY_ID_FIELD = "id"
private const val DEPENDENCY_SCOPE_FIELD = "scope"
private const val DEPENDENCY_SELECTED_REASON_FIELD = "selectionReason"
private const val DEPENDENCY_CLASSPATH_ORDER_FIELD = "classpathOrder"
private const val DEPENDENCY_EXPORTED_FIELD = "exported"
private const val DEPENDENCY_DEPENDENCIES_FIELD = "dependencies"

private const val DEPENDENCY_ID_GROUP_FIELD = "group"
private const val DEPENDENCY_ID_NAME_FIELD = "name"
private const val DEPENDENCY_ID_VERSION_FIELD = "version"
private const val DEPENDENCY_ID_PACKAGING_FIELD = "packaging"
private const val DEPENDENCY_ID_CLASSIFIER_FIELD = "classifier"

private const val LIBRARY_DEPENDENCY_FILE_FIELD = "file"
private const val LIBRARY_DEPENDENCY_SOURCE_FIELD = "source"
private const val LIBRARY_DEPENDENCY_JAVADOC_FIELD = "javadoc"

private const val MULTI_LIBRARY_DEPENDENCY_FILES_FIELD = "files"
private const val MULTI_LIBRARY_DEPENDENCY_SOURCES_FIELD = "sources"
private const val MULTI_LIBRARY_DEPENDENCY_JAVADOCS_FIELD = "javadocs"

private const val PROJECT_DEPENDENCY_PATH_FIELD = "projectPath"
private const val PROJECT_DEPENDENCY_CONFIGURATION_NAME_FIELD = "configurationName"
private const val PROJECT_DEPENDENCY_ARTIFACTS_FIELD = "projectDependencyArtifacts"
private const val PROJECT_DEPENDENCY_ARTIFACTS_SOURCES_FIELD = "projectDependencyArtifactsSources"

private const val FILE_COLLECTION_DEPENDENCY_FILES_FIELD = "files"
private const val FILE_COLLECTION_DEPENDENCY_EXCLUDED_FROM_INDEXING_FIELD = "excludedFromIndexing"

private const val UNRESOLVED_DEPENDENCY_FAILURE_MESSAGE_FIELD = "failureMessage"

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
        writeString(writer, DEPENDENCY_TYPE_FIELD, when (dependency) {
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
        return when (val type = readString(reader, DEPENDENCY_TYPE_FIELD)!!) {
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
  writeString(writer, DEPENDENCY_SCOPE_FIELD, dependency.scope)
  @Suppress("DEPRECATION")
  writeString(writer, DEPENDENCY_SELECTED_REASON_FIELD, dependency.selectionReason)
  writeInt(writer, DEPENDENCY_CLASSPATH_ORDER_FIELD, dependency.classpathOrder)
  writeBoolean(writer, DEPENDENCY_EXPORTED_FIELD, dependency.exported)
  writeCollection(writer, DEPENDENCY_DEPENDENCIES_FIELD, dependency.dependencies) {
    writeDependency(writer, context, it)
  }
}

private fun fillDependencyCommonFields(
  reader: IonReader,
  context: DependencyReadContext,
  dependency: AbstractExternalDependency,
) {
  fillDependencyId(reader, dependency.id as DefaultExternalDependencyId)
  dependency.scope = readString(reader, DEPENDENCY_SCOPE_FIELD)
  dependency.selectionReason = readString(reader, DEPENDENCY_SELECTED_REASON_FIELD)
  dependency.classpathOrder = readInt(reader, DEPENDENCY_CLASSPATH_ORDER_FIELD)
  dependency.exported = readBoolean(reader, DEPENDENCY_EXPORTED_FIELD)
  dependency.dependencies = readList(reader, DEPENDENCY_DEPENDENCIES_FIELD) {
    readDependency(reader, context)
  }
}

private fun writeDependencyId(writer: IonWriter, dependency: ExternalDependency) {
  writer.setFieldName(DEPENDENCY_ID_FIELD)
  writer.step(IonType.STRUCT) {
    val dependencyId = dependency.id
    writeString(writer, DEPENDENCY_ID_GROUP_FIELD, dependencyId.group)
    writeString(writer, DEPENDENCY_ID_NAME_FIELD, dependencyId.name)
    writeString(writer, DEPENDENCY_ID_VERSION_FIELD, dependencyId.version)
    writeString(writer, DEPENDENCY_ID_PACKAGING_FIELD, dependencyId.packaging)
    writeString(writer, DEPENDENCY_ID_CLASSIFIER_FIELD, dependencyId.classifier)
  }
}

private fun fillDependencyId(
  reader: IonReader,
  dependencyId: DefaultExternalDependencyId,
) {
  assertNotNull(reader.next())
  assertFieldName(reader, DEPENDENCY_ID_FIELD)
  reader.step {
    dependencyId.group = readString(reader, DEPENDENCY_ID_GROUP_FIELD)
    dependencyId.name = readString(reader, DEPENDENCY_ID_NAME_FIELD)
    dependencyId.version = readString(reader, DEPENDENCY_ID_VERSION_FIELD)
    dependencyId.packaging = readString(reader, DEPENDENCY_ID_PACKAGING_FIELD)!!
    dependencyId.classifier = readString(reader, DEPENDENCY_ID_CLASSIFIER_FIELD)
  }
}

private fun writeLibraryDependencyFields(writer: IonWriter, dependency: ExternalLibraryDependency) {
  writeFile(writer, LIBRARY_DEPENDENCY_FILE_FIELD, dependency.file)
  writeFile(writer, LIBRARY_DEPENDENCY_SOURCE_FIELD, dependency.source)
  writeFile(writer, LIBRARY_DEPENDENCY_JAVADOC_FIELD, dependency.javadoc)
}

private fun fillLibraryDependencyFields(reader: IonReader, dependency: DefaultExternalLibraryDependency) {
  dependency.file = readFile(reader, LIBRARY_DEPENDENCY_FILE_FIELD)
  dependency.source = readFile(reader, LIBRARY_DEPENDENCY_SOURCE_FIELD)
  dependency.javadoc = readFile(reader, LIBRARY_DEPENDENCY_JAVADOC_FIELD)
}

private fun writeMultiLibraryDependencyFields(writer: IonWriter, dependency: ExternalMultiLibraryDependency) {
  writeFiles(writer, MULTI_LIBRARY_DEPENDENCY_FILES_FIELD, dependency.files)
  writeFiles(writer, MULTI_LIBRARY_DEPENDENCY_SOURCES_FIELD, dependency.sources)
  writeFiles(writer, MULTI_LIBRARY_DEPENDENCY_JAVADOCS_FIELD, dependency.javadoc)
}

private fun fillMultiLibraryDependencyFields(reader: IonReader, dependency: DefaultExternalMultiLibraryDependency) {
  dependency.files.addAll(readFileList(reader, MULTI_LIBRARY_DEPENDENCY_FILES_FIELD))
  dependency.sources.addAll(readFileList(reader, MULTI_LIBRARY_DEPENDENCY_SOURCES_FIELD))
  dependency.javadoc.addAll(readFileList(reader, MULTI_LIBRARY_DEPENDENCY_JAVADOCS_FIELD))
}

private fun writeProjectDependencyFields(writer: IonWriter, dependency: ExternalProjectDependency) {
  writeString(writer, PROJECT_DEPENDENCY_PATH_FIELD, dependency.projectPath)
  writeString(writer, PROJECT_DEPENDENCY_CONFIGURATION_NAME_FIELD, dependency.configurationName)
  writeFiles(writer, PROJECT_DEPENDENCY_ARTIFACTS_FIELD, dependency.projectDependencyArtifacts)
  writeFiles(writer, PROJECT_DEPENDENCY_ARTIFACTS_SOURCES_FIELD, dependency.projectDependencyArtifactsSources)
}

private fun fillProjectDependencyFields(reader: IonReader, dependency: DefaultExternalProjectDependency) {
  dependency.projectPath = readString(reader, PROJECT_DEPENDENCY_PATH_FIELD)
  dependency.configurationName = readString(reader, PROJECT_DEPENDENCY_CONFIGURATION_NAME_FIELD)
  dependency.projectDependencyArtifacts = readFileList(reader, PROJECT_DEPENDENCY_ARTIFACTS_FIELD)
  dependency.projectDependencyArtifactsSources = readFileList(reader, PROJECT_DEPENDENCY_ARTIFACTS_SOURCES_FIELD)
}

private fun writeFileCollectionDependencyFields(writer: IonWriter, dependency: FileCollectionDependency) {
  writeFiles(writer, FILE_COLLECTION_DEPENDENCY_FILES_FIELD, dependency.files)
  writeBoolean(writer, FILE_COLLECTION_DEPENDENCY_EXCLUDED_FROM_INDEXING_FIELD, dependency.isExcludedFromIndexing)
}

private fun fillFileCollectionDependencyFields(reader: IonReader, dependency: DefaultFileCollectionDependency) {
  dependency.files = readFileList(reader, FILE_COLLECTION_DEPENDENCY_FILES_FIELD)
  dependency.isExcludedFromIndexing = readBoolean(reader, FILE_COLLECTION_DEPENDENCY_EXCLUDED_FROM_INDEXING_FIELD)
}

private fun writeUnresolvedDependencyFields(writer: IonWriter, dependency: UnresolvedExternalDependency) {
  writeString(writer, UNRESOLVED_DEPENDENCY_FAILURE_MESSAGE_FIELD, dependency.failureMessage)
}

private fun fillUnresolvedDependencyFields(reader: IonReader, dependency: DefaultUnresolvedExternalDependency) {
  dependency.failureMessage = readString(reader, UNRESOLVED_DEPENDENCY_FAILURE_MESSAGE_FIELD)
}