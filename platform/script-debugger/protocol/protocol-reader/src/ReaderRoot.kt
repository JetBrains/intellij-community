package org.jetbrains.protocolReader

import gnu.trove.THashSet
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.JsonParseMethod
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*

internal class ReaderRoot<R>(val type: Class<R>, private val typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>?>) {
  private val visitedInterfaces = THashSet<Class<*>>(1)
  val methodMap = LinkedHashMap<Method, ReadDelegate>();

  init {
    readInterfaceRecursive(type)
  }

  private fun readInterfaceRecursive(clazz: Class<*>) {
    if (visitedInterfaces.contains(clazz)) {
      return
    }
    visitedInterfaces.add(clazz)

    // todo sort by source location
    val methods = clazz.methods
    Arrays.sort<Method>(methods, object : Comparator<Method> {
      override fun compare(o1: Method, o2: Method) = o1.name.compareTo(o2.name)
    })

    for (m in methods) {
      m.getAnnotation<JsonParseMethod>(JsonParseMethod::class.java) ?: continue

      val exceptionTypes = m.exceptionTypes
      if (exceptionTypes.size > 1) {
        throw JsonProtocolModelParseException("Too many exception declared in " + m)
      }

      var returnType = m.genericReturnType
      var isList = false
      if (returnType is ParameterizedType) {
        val parameterizedType = returnType
        if (parameterizedType.rawType == List::class.java) {
          isList = true
          returnType = parameterizedType.actualTypeArguments[0]
        }
      }

      //noinspection SuspiciousMethodCalls
      var typeWriter: TypeWriter<*>? = typeToTypeHandler.getRaw(returnType)
      if (typeWriter == null) {
        typeWriter = createHandler(typeToTypeHandler, m.returnType)
      }

      val arguments = m.genericParameterTypes
      if (arguments.size > 2) {
        throw JsonProtocolModelParseException("Exactly one argument is expected in " + m)
      }
      val argument = arguments[0]
      if (argument == JsonReaderEx::class.java || argument == Any::class.java) {
        methodMap.put(m, ReadDelegate(typeWriter, isList, arguments.size != 1))
      }
      else {
        throw JsonProtocolModelParseException("Unrecognized argument type in " + m)
      }
    }

    for (baseType in clazz.genericInterfaces) {
      if (baseType !is Class<*>) {
        throw JsonProtocolModelParseException("Base interface must be class in " + clazz)
      }
      readInterfaceRecursive(baseType)
    }
  }

  public fun writeStaticMethodJava(scope: ClassScope) {
    val out = scope.output
    for (entry in methodMap.entries) {
      out.newLine()
      entry.value.write(scope, entry.key, out)
      out.newLine()
    }
  }
}