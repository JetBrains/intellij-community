// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.system.IonReaderBuilder
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.DependencyReadContext
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.DependencyWriteContext
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.readDependency
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.writeDependency
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*
import org.jetbrains.plugins.gradle.tooling.serialization.step
import java.io.ByteArrayOutputStream
import java.io.File

@ApiStatus.Internal
class GradleSourceSetSerialisationService : SerializationService<GradleSourceSetModel> {

  private val writeContext = SourceSetModelWriteContext()
  private val readContext = SourceSetModelReadContext()

  override fun getModelClass(): Class<out GradleSourceSetModel> {
    return GradleSourceSetModel::class.java
  }

  override fun write(`object`: GradleSourceSetModel, modelClazz: Class<out GradleSourceSetModel>): ByteArray {
    val out = ByteArrayOutputStream()
    createIonWriter().build(out).use { writer ->
      writeSourceSetModel(writer, writeContext, `object`)
    }
    return out.toByteArray()
  }

  override fun read(`object`: ByteArray, modelClazz: Class<out GradleSourceSetModel>): GradleSourceSetModel {
    IonReaderBuilder.standard().build(`object`).use { reader ->
      assertNotNull(reader.next())
      return readSourceSetModel(reader, readContext)
    }
  }

  class SourceSetModelReadContext {
    val dependencyContext = DependencyReadContext()
  }

  class SourceSetModelWriteContext {
    val dependencyContext = DependencyWriteContext()
  }

  companion object {

    private const val SOURCE_SET_MODEL_SOURCE_COMPATIBILITY_FIELD: String = "sourceCompatibility"
    private const val SOURCE_SET_MODEL_TARGET_COMPATIBILITY_FIELD: String = "targetCompatibility"
    private const val SOURCE_SET_MODEL_TASK_ARTIFACTS_FIELD: String = "taskArtifacts"
    private const val SOURCE_SET_MODEL_CONFIGURATION_ARTIFACTS_FIELD: String = "configurationArtifacts"
    private const val SOURCE_SET_MODEL_SOURCE_SETS_FIELD: String = "sourceSets"
    private const val SOURCE_SET_MODEL_ADDITIONAL_ARTIFACTS_FIELD: String = "additionalArtifacts"

    private const val SOURCE_SET_NAME_FIELD: String = "name"
    private const val SOURCE_SET_JAVA_TOOLCHAIN_FIELD: String = "javaToolchainHome"
    private const val SOURCE_SET_SOURCE_COMPATIBILITY_FIELD: String = "sourceCompatibility"
    private const val SOURCE_SET_TARGET_COMPATIBILITY_FIELD: String = "targetCompatibility"
    private const val SOURCE_SET_COMPILER_ARGUMENTS_FIELD: String = "compilerArguments"
    private const val SOURCE_SET_ARTIFACTS_FIELD: String = "artifacts"
    private const val SOURCE_SET_DEPENDENCIES_FIELD: String = "dependencies"
    private const val SOURCE_SET_SOURCES_FIELD: String = "sources"

    private const val SOURCE_DIRECTORY_NAME_FIELD: String = "name"
    private const val SOURCE_DIRECTORY_SRC_DIRS_FIELD: String = "srcDirs"
    private const val SOURCE_DIRECTORY_GRADLE_OUTPUTS_FIELD: String = "gradleOutputDirs"
    private const val SOURCE_DIRECTORY_OUTPUT_DIR_FIELD: String = "outputDir"
    private const val SOURCE_DIRECTORY_INHERIT_COMPILER_OUTPUT_FIELD: String = "inheritedCompilerOutput"
    private const val SOURCE_DIRECTORY_PATTERNS_FIELD: String = "patterns"
    private const val SOURCE_DIRECTORY_FILTERS_FIELD: String = "filters"

    private const val FILTER_TYPE_FIELD: String = "filterType"
    private const val FILTER_PROPERTIES_FIELD: String = "propertiesAsJsonMap"

    private const val PATTERNS_INCLUDES_FIELD: String = "includes"
    private const val PATTERNS_EXCLUDES_FIELD: String = "excludes"

    @JvmStatic
    fun writeSourceSetModel(writer: IonWriter, context: SourceSetModelWriteContext, model: GradleSourceSetModel) {
      writer.step(IonType.STRUCT) {
        writeString(writer, SOURCE_SET_MODEL_SOURCE_COMPATIBILITY_FIELD, model.sourceCompatibility)
        writeString(writer, SOURCE_SET_MODEL_TARGET_COMPATIBILITY_FIELD, model.targetCompatibility)
        writeFiles(writer, SOURCE_SET_MODEL_TASK_ARTIFACTS_FIELD, model.taskArtifacts)
        writeConfigurationArtifacts(writer, model)
        writeSourceSets(writer, context, model)
        writeFiles(writer, SOURCE_SET_MODEL_ADDITIONAL_ARTIFACTS_FIELD, model.additionalArtifacts)
      }
    }

    /**
     * This function doesn't advance the [reader].
     * Because it can be used as structure field reader and as element reader.
     */
    @JvmStatic
    fun readSourceSetModel(reader: IonReader, context: SourceSetModelReadContext): DefaultGradleSourceSetModel {
      return reader.step {
        DefaultGradleSourceSetModel().apply {
          sourceCompatibility = readString(reader, SOURCE_SET_MODEL_SOURCE_COMPATIBILITY_FIELD)
          targetCompatibility = readString(reader, SOURCE_SET_MODEL_TARGET_COMPATIBILITY_FIELD)
          taskArtifacts = readFileList(reader, SOURCE_SET_MODEL_TASK_ARTIFACTS_FIELD)
          configurationArtifacts = readConfigurationArtifacts(reader)
          sourceSets = readSourceSets(reader, context)
          additionalArtifacts = readFileList(reader, SOURCE_SET_MODEL_ADDITIONAL_ARTIFACTS_FIELD)
        }
      }
    }

    private fun writeConfigurationArtifacts(writer: IonWriter, sourceSetModel: GradleSourceSetModel) {
      writeMap(writer, SOURCE_SET_MODEL_CONFIGURATION_ARTIFACTS_FIELD, sourceSetModel.configurationArtifacts,
               { writeString(writer, MAP_KEY_FIELD, it) },
               { writeFiles(writer, MAP_VALUE_FIELD, it) })
    }

    private fun readConfigurationArtifacts(reader: IonReader): Map<String?, Set<File>> {
      return readMap(reader, SOURCE_SET_MODEL_CONFIGURATION_ARTIFACTS_FIELD,
                     { readString(reader, MAP_KEY_FIELD) },
                     { readFileSet(reader, MAP_VALUE_FIELD) })
    }

    private fun writeSourceSets(writer: IonWriter, context: SourceSetModelWriteContext, sourceSetModel: GradleSourceSetModel) {
      writeCollection(writer, SOURCE_SET_MODEL_SOURCE_SETS_FIELD, sourceSetModel.sourceSets.values as Collection<ExternalSourceSet>) {
        writeSourceSet(writer, context, it)
      }
    }

    private fun readSourceSets(reader: IonReader, context: SourceSetModelReadContext): Map<String, DefaultExternalSourceSet> {
      return readList(reader, SOURCE_SET_MODEL_SOURCE_SETS_FIELD) { readSourceSet(reader, context) }
        .associateBy { it.name }
    }

    private fun writeSourceSet(writer: IonWriter, context: SourceSetModelWriteContext, sourceSet: ExternalSourceSet) {
      writer.step(IonType.STRUCT) {
        writeString(writer, SOURCE_SET_NAME_FIELD, sourceSet.name)
        writeFile(writer, SOURCE_SET_JAVA_TOOLCHAIN_FIELD, sourceSet.javaToolchainHome)
        writeString(writer, SOURCE_SET_SOURCE_COMPATIBILITY_FIELD, sourceSet.sourceCompatibility)
        writeString(writer, SOURCE_SET_TARGET_COMPATIBILITY_FIELD, sourceSet.targetCompatibility)
        writeStrings(writer, SOURCE_SET_COMPILER_ARGUMENTS_FIELD, sourceSet.compilerArguments)
        writeFiles(writer, SOURCE_SET_ARTIFACTS_FIELD, sourceSet.artifacts)
        writeDependencies(writer, context, sourceSet)
        writeSourceDirectorySets(writer, sourceSet)
      }
    }

    private fun readSourceSet(reader: IonReader, context: SourceSetModelReadContext): DefaultExternalSourceSet? {
      if (reader.next() == null) return null
      return reader.step {
        DefaultExternalSourceSet().apply {
          name = readString(reader, SOURCE_SET_NAME_FIELD)!!
          javaToolchainHome = readFile(reader, SOURCE_SET_JAVA_TOOLCHAIN_FIELD)
          sourceCompatibility = readString(reader, SOURCE_SET_SOURCE_COMPATIBILITY_FIELD)
          targetCompatibility = readString(reader, SOURCE_SET_TARGET_COMPATIBILITY_FIELD)
          compilerArguments = readStringList(reader, SOURCE_SET_COMPILER_ARGUMENTS_FIELD)
          artifacts = readFileList(reader, SOURCE_SET_ARTIFACTS_FIELD)
          dependencies = readDependencies(reader, context)
          sources = readSourceDirectorySets(reader)
        }
      }
    }

    private fun writeDependencies(writer: IonWriter, context: SourceSetModelWriteContext, sourceSet: ExternalSourceSet) {
      writeCollection(writer, SOURCE_SET_DEPENDENCIES_FIELD, sourceSet.dependencies) {
        writeDependency(writer, context.dependencyContext, it!!)
      }
    }

    private fun readDependencies(reader: IonReader, context: SourceSetModelReadContext): Collection<ExternalDependency> {
      return readList(reader, SOURCE_SET_DEPENDENCIES_FIELD) {
        readDependency(reader, context.dependencyContext)
      }
    }

    private fun writeSourceDirectorySets(writer: IonWriter, sourceSet: ExternalSourceSet) {
      writeMap(writer, SOURCE_SET_SOURCES_FIELD, sourceSet.sources,
               { writeSourceDirectoryType(writer, it) },
               { writeSourceDirectorySet(writer, it) })
    }

    private fun readSourceDirectorySets(reader: IonReader): Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> {
      return readMap(reader, SOURCE_SET_SOURCES_FIELD,
                     { readSourceDirectoryType(reader) },
                     { readSourceDirectorySet(reader) })
    }

    private fun writeSourceDirectoryType(writer: IonWriter, sourceType: IExternalSystemSourceType) {
      writeString(writer, MAP_KEY_FIELD, ExternalSystemSourceType.from(sourceType).name)
    }

    private fun readSourceDirectoryType(reader: IonReader): ExternalSystemSourceType {
      return ExternalSystemSourceType.valueOf(readString(reader, MAP_KEY_FIELD)!!)
    }

    private fun writeSourceDirectorySet(writer: IonWriter, directorySet: ExternalSourceDirectorySet) {
      writer.setFieldName(MAP_VALUE_FIELD)
      writer.step(IonType.STRUCT) {
        writeString(writer, SOURCE_DIRECTORY_NAME_FIELD, directorySet.name)
        writeFiles(writer, SOURCE_DIRECTORY_SRC_DIRS_FIELD, directorySet.srcDirs)
        writeFiles(writer, SOURCE_DIRECTORY_GRADLE_OUTPUTS_FIELD, directorySet.gradleOutputDirs)
        writeFile(writer, SOURCE_DIRECTORY_OUTPUT_DIR_FIELD, directorySet.outputDir)
        writeBoolean(writer, SOURCE_DIRECTORY_INHERIT_COMPILER_OUTPUT_FIELD, directorySet.isCompilerOutputPathInherited)
        writePatterns(writer, directorySet)
        writeFilters(writer, directorySet)
      }
    }

    private fun readSourceDirectorySet(reader: IonReader): DefaultExternalSourceDirectorySet {
      assertNotNull(reader.next())
      assertFieldName(reader, MAP_VALUE_FIELD)
      return reader.step {
        DefaultExternalSourceDirectorySet().apply {
          name = readString(reader, SOURCE_DIRECTORY_NAME_FIELD)!!
          srcDirs = readFileSet(reader, SOURCE_DIRECTORY_SRC_DIRS_FIELD)
          gradleOutputDirs = readFileList(reader, SOURCE_DIRECTORY_GRADLE_OUTPUTS_FIELD)
          outputDir = readFile(reader, SOURCE_DIRECTORY_OUTPUT_DIR_FIELD)!!
          isCompilerOutputPathInherited = readBoolean(reader, SOURCE_DIRECTORY_INHERIT_COMPILER_OUTPUT_FIELD)
          patterns = readPatterns(reader)
          filters = readFilters(reader)
        }
      }
    }

    private fun writeFilters(writer: IonWriter, directorySet: ExternalSourceDirectorySet) {
      writeCollection(writer, SOURCE_DIRECTORY_FILTERS_FIELD, directorySet.filters) {
        writeFilter(writer, it)
      }
    }

    private fun readFilters(reader: IonReader): List<DefaultExternalFilter> {
      return readList(reader, SOURCE_DIRECTORY_FILTERS_FIELD) {
        readFilter(reader)
      }
    }

    private fun writeFilter(writer: IonWriter, filter: ExternalFilter) {
      writer.step(IonType.STRUCT) {
        writeString(writer, FILTER_TYPE_FIELD, filter.filterType)
        writeString(writer, FILTER_PROPERTIES_FIELD, filter.propertiesAsJsonMap)
      }
    }

    private fun readFilter(reader: IonReader): DefaultExternalFilter? {
      if (reader.next() == null) return null
      return reader.step {
        DefaultExternalFilter().apply {
          filterType = readString(reader, FILTER_TYPE_FIELD)!!
          propertiesAsJsonMap = readString(reader, FILTER_PROPERTIES_FIELD)!!
        }
      }
    }

    private fun writePatterns(writer: IonWriter, directorySet: ExternalSourceDirectorySet) {
      writer.setFieldName(SOURCE_DIRECTORY_PATTERNS_FIELD)
      writer.step(IonType.STRUCT) {
        val patterns = directorySet.patterns
        writeStrings(writer, PATTERNS_INCLUDES_FIELD, patterns.includes)
        writeStrings(writer, PATTERNS_EXCLUDES_FIELD, patterns.excludes)
      }
    }

    private fun readPatterns(reader: IonReader): FilePatternSet {
      assertNotNull(reader.next())
      assertFieldName(reader, SOURCE_DIRECTORY_PATTERNS_FIELD)
      return reader.step {
        FilePatternSetImpl().apply {
          includes = readStringSet(reader, PATTERNS_INCLUDES_FIELD)
          excludes = readStringSet(reader, PATTERNS_EXCLUDES_FIELD)
        }
      }
    }
  }
}