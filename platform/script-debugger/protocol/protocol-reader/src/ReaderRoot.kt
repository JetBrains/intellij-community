package org.jetbrains.protocolReader

import gnu.trove.THashSet
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.JsonParseMethod
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*

internal class ReaderRoot<R>(val type: Class<R>, private val typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>?>) {
  private val visitedInterfaces = THashSet<Class<*>>(1)
  val methodMap = LinkedHashMap<Method, ReadDelegate>()

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
    Arrays.sort<Method>(methods, { o1, o2 -> o1.name.compareTo(o2.name) })

    for (m in methods) {
      m.getAnnotation<JsonParseMethod>(JsonParseMethod::class.java) ?: continue

      val exceptionTypes = m.exceptionTypes
      if (exceptionTypes.size > 1) {
        throw JsonProtocolModelParseException("Too many exception declared in $m")
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
      var typeWriter: TypeWriter<*>? = typeToTypeHandler.get(returnType as Any?)
      if (typeWriter == null) {
        typeWriter = createHandler(typeToTypeHandler, m.returnType)
      }

      val arguments = m.genericParameterTypes
      if (arguments.size > 2) {
        throw JsonProtocolModelParseException("Exactly one argument is expected in $m")
      }
      val argument = arguments[0]
      if (argument == JsonReaderEx::class.java || argument == Any::class.java) {
        methodMap.put(m, ReadDelegate(typeWriter, isList, arguments.size != 1))
      }
      else {
        throw JsonProtocolModelParseException("Unrecognized argument type in $m")
      }
    }

    for (baseType in clazz.genericInterfaces) {
      if (baseType !is Class<*>) {
        throw JsonProtocolModelParseException("Base interface must be class in $clazz")
      }
      readInterfaceRecursive(baseType)
    }
  }

  fun write(scope: ClassScope) {
    val out = scope.output
    for (entry in methodMap.entries) {
      out.newLine()
      entry.value.write(scope, entry.key, out)
      out.newLine()
    }
  }
}

private val STATIC_METHOD_PARAM_NAME_LIST = listOf(READER_NAME)
private val STATIC_METHOD_PARAM_NAME_LIST2 = Arrays.asList(READER_NAME, "nextName")

internal class ReadDelegate(private val typeHandler: TypeWriter<*>, private val isList: Boolean, hasNextNameParam: Boolean) {
  private val paramNames = if (hasNextNameParam) STATIC_METHOD_PARAM_NAME_LIST2 else STATIC_METHOD_PARAM_NAME_LIST

  fun write(scope: ClassScope, method: Method, out: TextOutput) {
    writeMethodDeclarationJava(out, method, paramNames)
    out.append(": ")
    writeJavaTypeName(method.genericReturnType, out)
    out.append(" = ")
    if (isList) {
      out.append("readObjectArray(").append(READER_NAME).append(", ").append(TYPE_FACTORY_NAME_PREFIX).append(scope.requireFactoryGenerationAndGetName(typeHandler)).append("()").append(")")
    }
    else {
      typeHandler.writeInstantiateCode(scope, out)
      out.append('(').append(READER_NAME)
      out.comma().space()
      out.append(if (paramNames.size == 1) "null" else "nextName")
      out.append(')')
    }
  }
}