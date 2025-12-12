package com.intellij.remoteDev.tests.impl.utils

import java.io.*
import java.util.*
import kotlin.io.inputStream
import kotlin.use

/**
 * Executes a given lambda (it must be serializable) inside
 * IntelliJ-based IDE process with a special classloader,
 * where all classes of all plugins are visible.
 */

data class SerializedLambda(
  val clazzName: String,
  val methodName: String,
  val serializedDataBase64: String,
  val parametersBase64: List<String>,
  val classPath: Set<File>,
)

//this class is used from IntelliJ process
open class SerializedLambdaHelper {
  init {
    System.setProperty("sun.io.serialization.extendedDebugInfo", "true")
  }

  fun serialize(obj: Serializable): String = try {
    ByteArrayOutputStream().use {
      ObjectOutputStream(it).writeObject(obj)
      Base64.getEncoder().encodeToString(it.toByteArray())
    }
  }
  catch (t: Throwable) {
    throw Error("Failed to save/load the lambda. Most likely, " +
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

  fun <R : Serializable> decodeObject(stringToDecode: String, classLoader: ClassLoader = javaClass.classLoader): R? {
    val inputStream = Base64.getDecoder().decode(stringToDecode).inputStream()
    val obj = ClassLoaderObjectInputStream(inputStream, classLoader).readObject()
    return obj as? Serializable as? R
  }
}

