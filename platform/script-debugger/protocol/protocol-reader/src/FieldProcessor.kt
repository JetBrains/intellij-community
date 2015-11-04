package org.jetbrains.protocolReader

import gnu.trove.THashSet
import org.jetbrains.jsonProtocol.JsonField
import org.jetbrains.jsonProtocol.JsonOptionalField
import org.jetbrains.jsonProtocol.JsonSubtypeCasting
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*

internal class FieldLoader(val name: String, val jsonName: String, val valueReader: ValueReader, val skipRead: Boolean)

internal class FieldProcessor(private val reader: InterfaceReader, typeClass: Class<*>) {
  val fieldLoaders = ArrayList<FieldLoader>()
  val methodHandlerMap = LinkedHashMap<Method, MethodHandler>()
  val volatileFields = ArrayList<VolatileFieldBinding>()
  var lazyRead: Boolean = false

  init {
    val methods = typeClass.methods
    // todo sort by source location
    Arrays.sort(methods, { o1, o2 -> o1.name.compareTo(o2.name) })

    val skippedNames = THashSet<String>()
    for (method in methods) {
      val annotation = method.getAnnotation<JsonField>(JsonField::class.java)
      if (annotation != null && !annotation.primitiveValue.isEmpty()) {
        skippedNames.add(annotation.primitiveValue)
        skippedNames.add("${annotation.primitiveValue}Type")
      }
    }

    val classPackage = typeClass.`package`
    for (method in methods) {
      val methodClass = method.declaringClass
      // use method from super if super located in the same package
      if (methodClass != typeClass) {
        val methodPackage = methodClass.`package`
        // may be it will be useful later
        // && !methodPackage.getName().equals("org.jetbrains.debugger.adapters")
        if (methodPackage != classPackage) {
          continue
        }
      }

      if (method.parameterCount != 0) {
        throw JsonProtocolModelParseException("No parameters expected in $method")
      }

      try {
        val methodHandler: MethodHandler
        val jsonSubtypeCaseAnnotation = method.getAnnotation(JsonSubtypeCasting::class.java)
        if (jsonSubtypeCaseAnnotation == null) {
          methodHandler = createMethodHandler(method, skippedNames.contains(method.name))
        }
        else {
          methodHandler = processManualSubtypeMethod(method, jsonSubtypeCaseAnnotation)
          lazyRead = true
        }
        methodHandlerMap.put(method, methodHandler)
      }
      catch (e: Exception) {
        throw JsonProtocolModelParseException("Problem with method $method", e)
      }
    }
  }

  private fun createMethodHandler(method: Method, skipRead: Boolean): MethodHandler {
    var jsonName = method.name
    val fieldAnnotation = method.getAnnotation<JsonField>(JsonField::class.java)
    if (fieldAnnotation != null && !fieldAnnotation.name.isEmpty()) {
      jsonName = fieldAnnotation.name
    }

    val genericReturnType = method.genericReturnType
    val addNotNullAnnotation: Boolean
    val isPrimitive = if (genericReturnType is Class<*>) genericReturnType.isPrimitive else genericReturnType !is ParameterizedType
    if (isPrimitive) {
      addNotNullAnnotation = false
    }
    else if (fieldAnnotation != null) {
      addNotNullAnnotation = !fieldAnnotation.optional && !fieldAnnotation.allowAnyPrimitiveValue && !fieldAnnotation.allowAnyPrimitiveValueAndMap
    }
    else {
      addNotNullAnnotation = method.getAnnotation<JsonOptionalField>(JsonOptionalField::class.java) == null
    }

    val fieldTypeParser = reader.getFieldTypeParser(genericReturnType, false, method)
    if (fieldTypeParser != VOID_PARSER) {
      fieldLoaders.add(FieldLoader(method.name, jsonName, fieldTypeParser, skipRead))
    }

    val effectiveFieldName = if (fieldTypeParser == VOID_PARSER) null else method.name
    return object : MethodHandler {
      override fun writeMethodImplementationJava(scope: ClassScope, method: Method, out: TextOutput) {
        out.append("override fun ")

        val isEscapeName = method.name == "object"
        if (isEscapeName) {
          out.append('`')
        }
        out.append(method.name)
        if (isEscapeName) {
          out.append('`')
        }

        out.append("()")
        if (effectiveFieldName == null) {
          out.openBlock()
          out.closeBlock()
        }
        else {
          out.append(" = ").append(FIELD_PREFIX).append(effectiveFieldName)
          if (addNotNullAnnotation) {
            out.append("!!")
          }
        }
      }
    }
  }

  private fun processManualSubtypeMethod(m: Method, jsonSubtypeCaseAnn: JsonSubtypeCasting): MethodHandler {
    val fieldTypeParser = reader.getFieldTypeParser(m.genericReturnType, !jsonSubtypeCaseAnn.reinterpret, null)
    val fieldInfo = allocateVolatileField(fieldTypeParser, true)
    val handler = LazyCachedMethodHandler(fieldTypeParser, fieldInfo)
    val parserAsObjectValueParser = fieldTypeParser.asJsonTypeParser()
    if (parserAsObjectValueParser != null && parserAsObjectValueParser.isSubtyping()) {
      val subtypeCaster = object : SubtypeCaster(parserAsObjectValueParser.type) {
        override fun writeJava(out: TextOutput) {
          out.append(m.name).append("()")
        }
      }
      reader.subtypeCasters.add(subtypeCaster)
    }
    return handler
  }

  private fun allocateVolatileField(fieldTypeParser: ValueReader, internalType: Boolean): VolatileFieldBinding {
    val position = volatileFields.size
    val fieldTypeInfo: (scope: FileScope, out: TextOutput)->Unit
    if (internalType) {
      fieldTypeInfo = {scope, out -> fieldTypeParser.appendInternalValueTypeName(scope, out)}
    }
    else {
      fieldTypeInfo = {scope, out -> fieldTypeParser.appendFinishedValueTypeName(out)}
    }
    val binding = VolatileFieldBinding(position, fieldTypeInfo)
    volatileFields.add(binding)
    return binding
  }
}
