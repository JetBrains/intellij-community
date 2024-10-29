// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter
import com.amazon.ion.system.IonReaderBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultExternalTask
import org.jetbrains.plugins.gradle.model.ExternalTask
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*
import org.jetbrains.plugins.gradle.tooling.serialization.step
import java.io.ByteArrayOutputStream

@ApiStatus.Internal
class GradleTaskSerialisationService : SerializationService<GradleTaskModel> {

  override fun getModelClass(): Class<out GradleTaskModel> {
    return GradleTaskModel::class.java
  }

  override fun write(`object`: GradleTaskModel, modelClazz: Class<out GradleTaskModel>): ByteArray {
    val out = ByteArrayOutputStream()
    createIonWriter().build(out).use { writer ->
      writeTaskModel(writer, `object`)
    }
    return out.toByteArray()
  }

  override fun read(`object`: ByteArray, modelClazz: Class<out GradleTaskModel>): GradleTaskModel {
    IonReaderBuilder.standard().build(`object`).use { reader ->
      assertNotNull(reader.next())
      return readTaskModel(reader)
    }
  }

  companion object {

    private const val TASK_MODEL_TASKS_FIELD: String = "tasks"

    private const val TASK_NAME_FIELD: String = "name"
    private const val TASK_Q_NAME_FIELD: String = "qName"
    private const val TASK_DESCRIPTION_FIELD: String = "description"
    private const val TASK_GROUP_FIELD: String = "group"
    private const val TASK_TYPE_FIELD: String = "type"
    private const val TASK_IS_JVM_FIELD: String = "isJvm"
    private const val TASK_IS_TEST_FIELD: String = "isTest"
    private const val TASK_IS_JVM_TEST_FIELD: String = "isJvmTest"
    private const val TASK_IS_INHERITED_FIELD: String = "isInherited"

    @JvmStatic
    fun writeTaskModel(writer: IonWriter, model: GradleTaskModel) {
      writer.step(IonType.STRUCT) {
        writeTasks(writer, model)
      }
    }

    /**
     * This function doesn't advance the [reader].
     * Because it can be used as structure field reader and as element reader.
     */
    @JvmStatic
    fun readTaskModel(reader: IonReader): DefaultGradleTaskModel {
      return reader.step {
        DefaultGradleTaskModel().apply {
          tasks = readTasks(reader)
        }
      }
    }

    private fun writeTasks(writer: IonWriter, taskModel: GradleTaskModel) {
      writeMap(writer, TASK_MODEL_TASKS_FIELD, taskModel.tasks,
               { writeString(writer, MAP_KEY_FIELD, it) },
               { writeTask(writer, it) })
    }

    private fun readTasks(reader: IonReader): Map<String, DefaultExternalTask> {
      return readMap(reader, TASK_MODEL_TASKS_FIELD,
                     { readString(reader, MAP_KEY_FIELD) },
                     { readTask(reader) })
    }

    private fun writeTask(writer: IonWriter, task: ExternalTask) {
      writer.setFieldName(MAP_VALUE_FIELD)
      writer.step(IonType.STRUCT) {
        writeString(writer, TASK_NAME_FIELD, task.name)
        writeString(writer, TASK_Q_NAME_FIELD, task.qName)
        writeString(writer, TASK_DESCRIPTION_FIELD, task.description)
        writeString(writer, TASK_GROUP_FIELD, task.group)
        writeString(writer, TASK_TYPE_FIELD, task.type)
        writeBoolean(writer, TASK_IS_JVM_FIELD, task.isJvm)
        writeBoolean(writer, TASK_IS_TEST_FIELD, task.isTest)
        writeBoolean(writer, TASK_IS_JVM_TEST_FIELD, task.isJvmTest)
        writeBoolean(writer, TASK_IS_INHERITED_FIELD, task.isInherited)
      }
    }

    private fun readTask(reader: IonReader): DefaultExternalTask {
      assertNotNull(reader.next())
      assertFieldName(reader, MAP_VALUE_FIELD)
      return reader.step {
        DefaultExternalTask().apply {
          name = readString(reader, TASK_NAME_FIELD)!!
          qName = readString(reader, TASK_Q_NAME_FIELD)!!
          description = readString(reader, TASK_DESCRIPTION_FIELD)
          group = readString(reader, TASK_GROUP_FIELD)
          type = readString(reader, TASK_TYPE_FIELD)
          isJvm = readBoolean(reader, TASK_IS_JVM_FIELD)
          isTest = readBoolean(reader, TASK_IS_TEST_FIELD)
          isJvmTest = readBoolean(reader, TASK_IS_JVM_TEST_FIELD)
          isInherited = readBoolean(reader, TASK_IS_INHERITED_FIELD)
        }
      }
    }
  }
}