// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    // Reloaded through the loader that *defined* the lambda, not through this helper's own. They are the same
    // loader whenever the test classpath is flat, which is why the default held for so long — but a harness that
    // loads test code in a child classloader (the AIR UI-test daemon puts every test class in a hot child tier so
    // an iteration needs no reassembly) defines the lambda somewhere this class's loader cannot see, and the
    // check below then fails with `ClassNotFoundException` for a lambda that is perfectly serializable. The
    // point of the round trip is to prove the lambda can be reloaded at all; the loader that owns it is the only
    // one that can answer that.
    val reloadedLambda = getSuspendingSerializableConsumer<T, R>(persistedLambda, obj.javaClass.classLoader)
    require(reloadedLambda.javaClass == obj.javaClass) {
      "The reloaded lambda should have the same type as the original one. " +
      "Reloaded Type is ${reloadedLambda.javaClass.name}, expected type is ${obj.javaClass.name}"
    }

    return SerializedLambda(
      serializedDataBase64 = persistedLambda,
      parametersBase64 = parameters.map { serialize(it) },
      classPath = clazzPath
    )
  }
}