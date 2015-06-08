package org.jetbrains.protocolReader

import gnu.trove.THashSet
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.JsonField
import org.jetbrains.jsonProtocol.JsonOptionalField
import org.jetbrains.jsonProtocol.JsonSubtype
import org.jetbrains.jsonProtocol.StringIntPair
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.ArrayList
import java.util.LinkedHashMap

fun InterfaceReader(protocolInterfaces: Array<Class<*>>): InterfaceReader {
  val map = LinkedHashMap<Class<*>, TypeWriter<*>>(protocolInterfaces.size())
  for (typeClass in protocolInterfaces) {
    map.put(typeClass, null)
  }
  return InterfaceReader(map)
}

private val LONG_PARSER = PrimitiveValueReader("long", "-1")

private val INTEGER_PARSER = PrimitiveValueReader("int", "-1")

private val BOOLEAN_PARSER = PrimitiveValueReader("boolean")
private val FLOAT_PARSER = PrimitiveValueReader("float")

private val NUMBER_PARSER = PrimitiveValueReader("double")

private val STRING_PARSER = PrimitiveValueReader("String")
private val NULLABLE_STRING_PARSER = PrimitiveValueReader(className = "String", nullable = true)

private val RAW_STRING_PARSER = PrimitiveValueReader("String", null, true)
private val RAW_STRING_OR_MAP_PARSER = object : PrimitiveValueReader("Object", null, true) {
  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    out.append("readRawStringOrMap(")
    addReaderParameter(subtyping, out)
    out.append(')')
  }
}

private val JSON_PARSER = RawValueReader()

private val STRING_INT_PAIR_PARSER = StringIntPairValueReader()

val VOID_PARSER: ValueReader = object : ValueReader() {
  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append("void")
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    out.append("null")
  }
}

fun createHandler(typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>>, aClass: Class<*>): TypeWriter<*> {
  val reader = InterfaceReader(typeToTypeHandler)
  reader.processed.addAll(typeToTypeHandler.keySet())
  reader.go(arrayOf(aClass))
  return typeToTypeHandler.get(aClass)
}

class InterfaceReader(val typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>>) {
  val processed = THashSet<Class<*>>()
  private val refs = ArrayList<TypeRef<*>>()
  val subtypeCasters = ArrayList<SubtypeCaster>()

  fun go(): LinkedHashMap<Class<*>, TypeWriter<*>> {
    return go(typeToTypeHandler.keySet().toTypedArray())
  }

  fun go(classes: Array<Class<*>>): LinkedHashMap<Class<*>, TypeWriter<*>> {
    for (typeClass in classes) {
      createIfNotExists(typeClass)
    }

    var hasUnresolved = true
    while (hasUnresolved) {
      hasUnresolved = false
      // refs can be modified - new items can be added
      for (i in 0..refs.size() - 1) {
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
      }
    }

    for (subtypeCaster in subtypeCasters) {
      subtypeCaster.getSubtypeHandler().subtypeAspect?.setSubtypeCaster(subtypeCaster)
    }

    return typeToTypeHandler
  }

  private fun createIfNotExists(typeClass: Class<*>) {
    if (typeClass == javaClass<Map<Any, Any>>() || typeClass == javaClass<List<Any>>() || !typeClass.isInterface()) {
      return
    }

    if (!processed.add(typeClass)) {
      return
    }

    typeToTypeHandler.put(typeClass, null)

    for (aClass in typeClass.getDeclaredClasses()) {
      createIfNotExists(aClass)
    }

    if (!typeClass.isInterface()) {
      throw JsonProtocolModelParseException("Json model type should be interface: " + typeClass.getName())
    }

    val fields = FieldProcessor(this, typeClass)
    for (method in fields.methodHandlerMap.keySet()) {
      val returnType = method.getReturnType()
      if (returnType != typeClass) {
        createIfNotExists(returnType)
      }
    }

    val typeWriter = TypeWriter(typeClass, getSuperclassRef(typeClass), fields.volatileFields, fields.methodHandlerMap, fields.fieldLoaders, fields.lazyRead)
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
    if (type is Class<*>) {
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
          if (jsonField != null && jsonField.allowAnyPrimitiveValue) {
            return RAW_STRING_PARSER
          }
          else if ((jsonField != null && jsonField.optional) || method.getAnnotation<JsonOptionalField>(javaClass<JsonOptionalField>()) != null) {
            return NULLABLE_STRING_PARSER
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
      else if (type == javaClass<StringIntPair>()) {
        return STRING_INT_PAIR_PARSER
      }
      else if (type.isArray()) {
        return ArrayReader(getFieldTypeParser(type.getComponentType(), false, null), false)
      }
      else if (type.isEnum()) {
        return EnumReader(type as Class<Enum<*>>)
      }
      val ref = getTypeRef(type)
      if (ref != null) {
        return ObjectValueReader(ref, isSubtyping, method?.getAnnotation<JsonField>(javaClass<JsonField>())?.primitiveValue)
      }
      throw UnsupportedOperationException("Method return type " + type + " (simple class) not supported")
    }
    else if (type is ParameterizedType) {
      val isList = type.getRawType() == javaClass<List<Any>>()
      if (isList || type.getRawType() == javaClass<Map<Any, Any>>()) {
        var argumentType = type.getActualTypeArguments()[if (isList) 0 else 1]
        if (argumentType is WildcardType) {
          val wildcard = argumentType
          if (wildcard.getLowerBounds().size() == 0 && wildcard.getUpperBounds().size() == 1) {
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
    val result = TypeRef(typeClass)
    refs.add(result)
    return result
  }

  private fun getSuperclassRef(typeClass: Class<*>): TypeRef<*>? {
    var result: TypeRef<*>? = null
    for (interfaceGeneric in typeClass.getGenericInterfaces()) {
      if (interfaceGeneric !is ParameterizedType) {
        continue
      }
      if (interfaceGeneric.getRawType() != javaClass<JsonSubtype<Any>>()) {
        continue
      }
      val param = interfaceGeneric.getActualTypeArguments()[0]
      if (param !is Class<*>) {
        throw JsonProtocolModelParseException("Unexpected type of superclass " + param)
      }
      if (result != null) {
        throw JsonProtocolModelParseException("Already has superclass " + result.typeClass.getName())
      }
      result = getTypeRef(param)
      if (result == null) {
        throw JsonProtocolModelParseException("Unknown base class " + param.getName())
      }
    }
    return result
  }
}