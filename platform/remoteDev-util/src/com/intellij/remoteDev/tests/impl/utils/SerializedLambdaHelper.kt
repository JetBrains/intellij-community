package com.intellij.remoteDev.tests.impl.utils

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.PathManager
import com.intellij.remoteDev.tests.LambdaIdeContext
import java.io.*
import java.util.*
import kotlin.io.inputStream
import kotlin.use

/**
 * Executes a given lambda (it must be serializable) inside
 * IntelliJ-based IDE process with a special classloader,
 * where all classes of all plugins are visible.
 * 
 * https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html
 */

fun interface SuspendingSerializableConsumer<T : LambdaIdeContext> : Serializable {
  suspend fun runSerializedLambda(lambdaIdeContext: T, parameters: List<Serializable>): Serializable
}

data class SerializedLambda(
  val clazzName: String,
  val methodName: String,
  val serializedDataBase64: String,
  val classPath: Set<File>,
) {
  companion object {
    init {
      System.setProperty("sun.io.serialization.extendedDebugInfo", "true")
    }

    inline fun <T : LambdaIdeContext> fromLambdaWithIdeContext(
      name: String?,
      crossinline serializedLambda: suspend T.(List<Serializable>) -> Serializable,
    ): SerializedLambda {

      return wrapLambda(name,
                        SuspendingSerializableConsumer<T> { lambdaIdeContext, parameters ->
                          serializedLambda(lambdaIdeContext,
                                           parameters)
                        })
    }
  }
}


//this class is used from IntelliJ process
class SerializedLambdaLoader {
  fun save(name: String?, obj: Any): String = try {
    ByteArrayOutputStream().use {
      ObjectOutputStream(it).writeObject(obj)
      Base64.getEncoder().encodeToString(it.toByteArray())
    }
  }
  catch (t: Throwable) {
    throw Error("Failed to save/load the lambda${name?.let { " '$it'" }}. Most likely, " +
                "the current lambda was more complex and so Kotlin compiler decided " +
                "to generate a more complicated constructor for a wrapper class. " +
                "Try to add java.io.Serializable, simplify the code, cleanup variables from the closure, copy parameters to the local scope. ${t.message}",
                t)
  }

  class ClassLoaderObjectInputStream(
    inputStream: InputStream,
    private val classLoader: ClassLoader,
  ) : ObjectInputStream(inputStream) {

    override fun resolveClass(desc: ObjectStreamClass): Class<*> {
      return Class.forName(desc.name, false, classLoader)
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : LambdaIdeContext> load(
    stringToDecode: String,
    classLoader: ClassLoader = javaClass.classLoader,
  ): SuspendingSerializableConsumer<T> {
    return loadObject(stringToDecode, classLoader) as? SuspendingSerializableConsumer<T>
           ?: error("Failed to load Consumer<T : LambdaIdeContext> from the lambda")
  }

  fun loadObject(stringToDecode: String, classLoader: ClassLoader = javaClass.classLoader): Serializable {
    val inputStream = Base64.getDecoder().decode(stringToDecode).inputStream()
    val obj = ClassLoaderObjectInputStream(inputStream, classLoader).readObject()
    return obj as? Serializable ?: error("Failed to load Serializable object from Base64 payload; object type is ${obj?.javaClass?.name}")
  }
}

fun <T : LambdaIdeContext> wrapLambda(name: String?, obj: SuspendingSerializableConsumer<T>): SerializedLambda {
  val clazzPath = setOf(SerializedLambdaLoader::class.java, obj.javaClass, Application::class.java)
    .mapNotNull { PathManager.getJarPathForClass(it) }
    .map { File(it) }
    .toSet()

  val persistedLambda = SerializedLambdaLoader().save(name, obj)
  val reloadedLambda = SerializedLambdaLoader().load<T>(persistedLambda)
  require(reloadedLambda.javaClass == obj.javaClass) {
    "The reloaded lambda should have the same type as the original one. " +
    "Reloaded Type is ${reloadedLambda.javaClass.name}, expected type is ${obj.javaClass.name}"
  }

  return SerializedLambda(
    clazzName = obj.javaClass.name,
    methodName = obj::runSerializedLambda.name,
    serializedDataBase64 = persistedLambda,
    classPath = clazzPath
  )
}

