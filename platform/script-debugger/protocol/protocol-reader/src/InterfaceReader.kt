package org.jetbrains.protocolReader

import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.JsonField
import org.jetbrains.jsonProtocol.JsonSubtype
import org.jetbrains.jsonProtocol.StringIntPair

import java.lang.annotation.RetentionPolicy
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.*


fun InterfaceReader(protocolInterfaces: Array<Class<*>>): InterfaceReader {
  val __ = InterfaceReader(LinkedHashMap<Class<*>, TypeWriter<*>>(protocolInterfaces.size), null, null, null)
  for (typeClass in protocolInterfaces) {
    __.typeToTypeHandler.put(typeClass, null)
  }
  return __
}

private fun InterfaceReader(typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>>): InterfaceReader {
  return InterfaceReader(typeToTypeHandler, null, null, null)
}

class InterfaceReader(
    private val typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>>,
    val refs: MutableList<TypeRef<*>>, val subtypeCasters: List<SubtypeCaster>,
    private val processed: MutableSet<Class<*>>) {

  fun go(): LinkedHashMap<Class<*>, TypeWriter<*>> {
    return go(typeToTypeHandler.keySet().toArray<Class<Any>>(arrayOfNulls<Class<Any>>(typeToTypeHandler.size())))
  }

  private fun go(classes: Array<Class<*>>): LinkedHashMap<Class<*>, TypeWriter<*>> {
    for (typeClass in classes) {
      createIfNotExists(typeClass)
    }

    var hasUnresolved = true
    while (hasUnresolved) {
      hasUnresolved = false
      // refs can be modified - new items can be added
      //noinspection ForLoopReplaceableByForEach
      run {
        var i = 0
        val n = refs.size()
        while (i < n) {
          val ref = refs.get(i)
          ref.type = typeToTypeHandler.get(ref.typeClass)
          if (ref.type == null) {
            createIfNotExists(ref.typeClass)
            hasUnresolved = true
            ref.type = typeToTypeHandler.get(ref.typeClass)
            if (ref.type == null) {
              throw IllegalStateException()
            }
          }
          i++
        }
      }
    }

    for (subtypeCaster in subtypeCasters) {
      val subtypeSupport = subtypeCaster.getSubtypeHandler().subtypeAspect
      if (subtypeSupport != null) {
        subtypeSupport!!.setSubtypeCaster(subtypeCaster)
      }
    }

    return typeToTypeHandler
  }

  private fun createIfNotExists(typeClass: Class<*>) {
    if (typeClass == javaClass<Map<Any, Any>>() || typeClass == javaClass<List<Any>>() || !typeClass.isInterface()) {
      return
    }

    if (processed.contains(typeClass)) {
      return
    }
    processed.add(typeClass)

    typeToTypeHandler.put(typeClass, null)

    for (aClass in typeClass.getDeclaredClasses()) {
      createIfNotExists(aClass)
    }

    if (!typeClass.isInterface()) {
      throw JsonProtocolModelParseException("Json model type should be interface: " + typeClass.getName())
    }

    val fields = FieldProcessor<Any>(this, typeClass)
    for (method in fields.methodHandlerMap.keySet()) {
      val returnType = method.getReturnType()
      if (returnType != typeClass) {
        createIfNotExists(returnType)
      }
    }

    val typeWriter = TypeWriter<Any>(typeClass, getSuperclassRef(typeClass), fields.volatileFields, fields.methodHandlerMap, fields.fieldLoaders, fields.lazyRead)
    for (ref in refs) {
      if (ref.typeClass == typeClass) {
        assert(ref.type == null)
        ref.type = typeWriter
        break
      }
    }
    typeToTypeHandler.put(typeClass, typeWriter)
  }

  fun getFieldTypeParser(type: Type, isSubtyping: Boolean, method: Method?): ValueReader {
    if (type is Class<Any>) {
      val typeClass = type as Class<*>
      if (type == java.lang.Long.TYPE) {
        return LONG_PARSER
      }
      else if (type == Integer.TYPE) {
        return INTEGER_PARSER
      }
      else if (type == java.lang.Boolean.TYPE) {
        return BOOLEAN_PARSER
      }
      else if (type == java.lang.Float.TYPE) {
        return FLOAT_PARSER
      }
      else if (type == javaClass<Number>() || type == java.lang.Double.TYPE) {
        return NUMBER_PARSER
      }
      else if (type == Void.TYPE) {
        return VOID_PARSER
      }
      else if (type == javaClass<String>()) {
        if (method != null) {
          val jsonField = method.getAnnotation<JsonField>(javaClass<JsonField>())
          if (jsonField != null && jsonField.allowAnyPrimitiveValue()) {
            return RAW_STRING_PARSER
          }
        }
        return STRING_PARSER
      }
      else if (type == javaClass<Any>()) {
        return RAW_STRING_OR_MAP_PARSER
      }
      else if (type == javaClass<JsonReaderEx>()) {
        return JSON_PARSER
      }
      else if (type == javaClass<Map<Any, Any>>()) {
        return MAP_PARSER
      }
      else if (type == javaClass<StringIntPair>()) {
        return STRING_INT_PAIR_PARSER
      }
      else if (typeClass.isArray()) {
        return ArrayReader(getFieldTypeParser(typeClass.getComponentType(), false, null), false)
      }
      else if (typeClass.isEnum()) {
        //noinspection unchecked
        return EnumReader.create(typeClass as Class<RetentionPolicy>)
      }
      val ref = getTypeRef<*>(typeClass)
      if (ref != null) {
        val jsonField = if (method == null) null else method.getAnnotation<JsonField>(javaClass<JsonField>())
        return ObjectValueReader<Any>(ref, isSubtyping, if (jsonField == null) null else jsonField.primitiveValue())
      }
      throw UnsupportedOperationException("Method return type " + type + " (simple class) not supported")
    }
    else if (type is ParameterizedType) {
      val parameterizedType = type as ParameterizedType
      val isList = parameterizedType.getRawType() == javaClass<List<Any>>()
      if (isList || parameterizedType.getRawType() == javaClass<Map<Any, Any>>()) {
        var argumentType = parameterizedType.getActualTypeArguments()[if (isList) 0 else 1]
        if (argumentType is WildcardType) {
          val wildcard = argumentType as WildcardType
          if (wildcard.getLowerBounds().size == 0 && wildcard.getUpperBounds().size == 1) {
            argumentType = wildcard.getUpperBounds()[0]
          }
        }
        val componentParser = getFieldTypeParser(argumentType, false, method)
        return if (isList) ArrayReader(componentParser, true) else MapReader(componentParser)
      }
      else {
        throw UnsupportedOperationException("Method return type " + type + " (generic) not supported")
      }
    }
    else {
      throw UnsupportedOperationException("Method return type " + type + " not supported")
    }
  }

  fun <T> getTypeRef(typeClass: Class<T>): TypeRef<T>? {
    val result = TypeRef<T>(typeClass)
    refs.add(result)
    return result
  }

  private fun getSuperclassRef(typeClass: Class<*>): TypeRef<*> {
    var result: TypeRef<*>? = null
    for (interfaceGeneric in typeClass.getGenericInterfaces()) {
      if (!(interfaceGeneric is ParameterizedType)) {
        continue
      }
      val parameterizedType = interfaceGeneric as ParameterizedType
      if (parameterizedType.getRawType() != javaClass<JsonSubtype<Any>>()) {
        continue
      }
      val param = parameterizedType.getActualTypeArguments()[0]
      if (!(param is Class<Any>)) {
        throw JsonProtocolModelParseException("Unexpected type of superclass " + param)
      }
      val paramClass = param as Class<*>
      if (result != null) {
        throw JsonProtocolModelParseException("Already has superclass " + result!!.typeClass.getName())
      }
      result = getTypeRef<*>(paramClass)
      if (result == null) {
        throw JsonProtocolModelParseException("Unknown base class " + paramClass.getName())
      }
    }
    return result
  }

  class object {
    private val LONG_PARSER = PrimitiveValueReader("long", "-1")

    private val INTEGER_PARSER = PrimitiveValueReader("int", "-1")

    private val BOOLEAN_PARSER = PrimitiveValueReader("boolean")
    private val FLOAT_PARSER = PrimitiveValueReader("float")

    private val NUMBER_PARSER = PrimitiveValueReader("double")

    private val STRING_PARSER = PrimitiveValueReader("String")

    private val RAW_STRING_PARSER = PrimitiveValueReader("String", null, true)
    private val RAW_STRING_OR_MAP_PARSER = object : PrimitiveValueReader("Object", null, true) {
      fun writeReadCode(methodScope: ClassScope, subtyping: Boolean, out: TextOutput) {
        out.append("readRawStringOrMap(")
        addReaderParameter(subtyping, out)
        out.append(')')
      }
    }

    private val JSON_PARSER = RawValueReader(false)

    private val MAP_PARSER = MapReader(null)

    private val STRING_INT_PAIR_PARSER = StringIntPairValueReader()

    val VOID_PARSER: ValueReader = object : ValueReader() {
      public fun appendFinishedValueTypeName(out: TextOutput) {
        out.append("void")
      }

      fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
        out.append("null")
      }
    }

    public fun createHandler(typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>>, aClass: Class<*>): TypeWriter<*> {
      val reader = InterfaceReader(typeToTypeHandler)
      reader.processed.addAll(typeToTypeHandler.keySet())
      reader.go(array<Class<Any>>(aClass))
      return typeToTypeHandler.get(aClass)
    }
  }
}