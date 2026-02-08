// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.system.IonReaderBuilder
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.DependencyReadContext
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.DependencyWriteContext
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.readDependency
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.writeDependency
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.ExternalDependency
import org.jetbrains.plugins.gradle.model.GradleSourceSetDependencyModel
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.MAP_KEY_FIELD
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.MAP_VALUE_FIELD
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.assertNotNull
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.createIonWriter
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readList
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readMap
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.readString
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeCollection
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeMap
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.writeString
import org.jetbrains.plugins.gradle.tooling.serialization.step
import java.io.ByteArrayOutputStream

@ApiStatus.Internal
class GradleSourceSetDependencySerialisationService : SerializationService<GradleSourceSetDependencyModel> {

  private val writeContext = SourceSetDependencyModelWriteContext()
  private val readContext = SourceSetDependencyModelReadContext()

  override fun getModelClass(): Class<out GradleSourceSetDependencyModel> {
    return GradleSourceSetDependencyModel::class.java
  }

  override fun write(`object`: GradleSourceSetDependencyModel, modelClazz: Class<out GradleSourceSetDependencyModel>): ByteArray {
    val out = ByteArrayOutputStream()
    createIonWriter().build(out).use { writer ->
      writeSourceSetDependencyModel(writer, writeContext, `object`)
    }
    return out.toByteArray()
  }

  override fun read(`object`: ByteArray, modelClazz: Class<out GradleSourceSetDependencyModel>): GradleSourceSetDependencyModel {
    IonReaderBuilder.standard().build(`object`).use { reader ->
      return readSourceSetDependencyModel(reader, readContext)
    }
  }

  private class SourceSetDependencyModelReadContext {
    val dependencyContext = DependencyReadContext()
  }

  private class SourceSetDependencyModelWriteContext {
    val dependencyContext = DependencyWriteContext()
  }

  companion object {

    private const val DEPENDENCIES_FILED = "dependencies"

    private fun writeSourceSetDependencyModel(
      writer: IonWriter,
      context: SourceSetDependencyModelWriteContext,
      model: GradleSourceSetDependencyModel,
    ) {
      writer.step(IonType.STRUCT) {
        writeDependencies(writer, context, model)
      }
    }

    private fun readSourceSetDependencyModel(
      reader: IonReader,
      context: SourceSetDependencyModelReadContext,
    ): GradleSourceSetDependencyModel {
      assertNotNull(reader.next())
      return reader.step {
        DefaultGradleSourceSetDependencyModel().apply {
          dependencies = readDependencies(reader, context)
        }
      }
    }

    private fun writeDependencies(
      writer: IonWriter,
      context: SourceSetDependencyModelWriteContext,
      model: GradleSourceSetDependencyModel,
    ) {
      writeMap(writer, DEPENDENCIES_FILED, model.dependencies, { writeString(writer, MAP_KEY_FIELD, it) }) {
        writeCollection(writer, MAP_VALUE_FIELD, it) { dependency ->
          writeDependency(writer, context.dependencyContext, dependency)
        }
      }
    }

    private fun readDependencies(
      reader: IonReader,
      context: SourceSetDependencyModelReadContext,
    ): Map<String, Collection<ExternalDependency>> {
      return readMap(reader, DEPENDENCIES_FILED, { readString(reader, MAP_KEY_FIELD) }) {
        readList(reader, MAP_VALUE_FIELD) {
          readDependency(reader, context.dependencyContext)
        }
      }
    }
  }
}