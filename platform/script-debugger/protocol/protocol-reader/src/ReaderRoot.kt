package org.jetbrains.protocolReader

import gnu.trove.THashSet
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.JsonParseMethod
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.Arrays
import java.util.Comparator
import java.util.LinkedHashMap

class ReaderRoot<R>(public val type: Class<R>, private val typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>>) {
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
    val methods = clazz.getMethods()
    Arrays.sort<Method>(methods, object : Comparator<Method> {
      override fun compare(o1: Method, o2: Method): Int {
        return o1.getName().compareTo(o2.getName())
      }
    })

    for (m in methods) {
      val jsonParseMethod = m.getAnnotation<JsonParseMethod>(javaClass<JsonParseMethod>())
      if (jsonParseMethod == null) {
        continue
      }

      val exceptionTypes = m.getExceptionTypes()
      if (exceptionTypes.size() > 1) {
        throw JsonProtocolModelParseException("Too many exception declared in " + m)
      }

      var returnType = m.getGenericReturnType()
      var isList = false
      if (returnType is ParameterizedType) {
        val parameterizedType = returnType as ParameterizedType
        if (parameterizedType.getRawType() == javaClass<List<Any>>()) {
          isList = true
          returnType = parameterizedType.getActualTypeArguments()[0]
        }
      }

      //noinspection SuspiciousMethodCalls
      var typeWriter: TypeWriter<*>? = typeToTypeHandler.get(returnType)
      if (typeWriter == null) {
        typeWriter = createHandler(typeToTypeHandler, m.getReturnType())
        if (typeWriter == null) {
          throw JsonProtocolModelParseException("Unknown return type in " + m)
        }
      }

      val arguments = m.getGenericParameterTypes()
      if (arguments.size() > 2) {
        throw JsonProtocolModelParseException("Exactly one argument is expected in " + m)
      }
      val argument = arguments[0]
      if (argument == javaClass<JsonReaderEx>() || argument == javaClass<Any>()) {
        methodMap.put(m, ReadDelegate(typeWriter!!, isList, arguments.size() != 1))
      }
      else {
        throw JsonProtocolModelParseException("Unrecognized argument type in " + m)
      }
    }

    for (baseType in clazz.getGenericInterfaces()) {
      if (baseType !is Class<*>) {
        throw JsonProtocolModelParseException("Base interface must be class in " + clazz)
      }
      readInterfaceRecursive(baseType)
    }
  }

  public fun writeStaticMethodJava(scope: ClassScope) {
    val out = scope.output
    for (entry in methodMap.entrySet()) {
      out.newLine()
      entry.getValue().write(scope, entry.getKey(), out)
      out.newLine()
    }
  }
}