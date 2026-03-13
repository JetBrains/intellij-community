package com.intellij.remoteDev.tests.impl.utils

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.remoteDev.tests.LambdaIdeContext
import java.io.File
import java.io.Serializable

class SerializedLambdaWithIdeContextHelper: SerializedLambdaHelper() {
  fun interface SuspendingSerializableConsumer<T : LambdaIdeContext, R> : Serializable {
    suspend fun T.runSerializedLambda(parameters: List<Serializable>): R
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : LambdaIdeContext, R : Any> getSuspendingSerializableConsumer(
    stringToDecode: String,
    classLoader: ClassLoader = javaClass.classLoader,
  ): SuspendingSerializableConsumer<T, R> {
    return decodeObject(stringToDecode, classLoader) as? SuspendingSerializableConsumer<T, R>
           ?: error("Failed to load Consumer<T : LambdaIdeContext> from the lambda")
  }

  fun <T : LambdaIdeContext, R : Any> getSerializedLambda(parameters: List<Serializable>, obj: SuspendingSerializableConsumer<T, R>): SerializedLambda {
    val clazzPath = setOf(SerializedLambdaHelper::class.java, obj.javaClass, Application::class.java)
      .mapNotNull { PathManager.getJarPathForClass(it) }
      .map { File(it) }
      .toSet()

    val persistedLambda = serialize(obj)
    val reloadedLambda = getSuspendingSerializableConsumer<T, R>(persistedLambda)
    require(reloadedLambda.javaClass == obj.javaClass) {
      "The reloaded lambda should have the same type as the original one. " +
      "Reloaded Type is ${reloadedLambda.javaClass.name}, expected type is ${obj.javaClass.name}"
    }

    return SerializedLambda(
      clazzName = obj.javaClass.name,
      methodName = "runSerializedLambda",
      serializedDataBase64 = persistedLambda,
      parametersBase64 = parameters.map { serialize(it) },
      classPath = clazzPath
    )
  }
}