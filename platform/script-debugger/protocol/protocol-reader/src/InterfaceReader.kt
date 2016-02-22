package org.jetbrains.protocolReader

import gnu.trove.THashSet
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.jsonProtocol.JsonField
import org.jetbrains.jsonProtocol.JsonSubtype
import org.jetbrains.jsonProtocol.Optional
import org.jetbrains.jsonProtocol.StringIntPair
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.*
import kotlin.reflect.KCallable

internal fun InterfaceReader(protocolInterfaces: List<Class<*>>): InterfaceReader {
  val map = LinkedHashMap<Class<*>, TypeWriter<*>?>(protocolInterfaces.size)
  for (typeClass in protocolInterfaces) {
    map.put(typeClass, null)
  }
  return InterfaceReader(map)
}

private val LONG_PARSER = PrimitiveValueReader("Long", "-1L")

private val INTEGER_PARSER = PrimitiveValueReader("Int", "-1")

private val BOOLEAN_PARSER = PrimitiveValueReader("Boolean", "false")
private val FLOAT_PARSER = PrimitiveValueReader("Float")

private val NUMBER_PARSER = PrimitiveValueReader("Double", "Double.NaN")

private val STRING_PARSER = PrimitiveValueReader("String")
private val NULLABLE_STRING_PARSER = PrimitiveValueReader(className = "String", nullable = true)

private val RAW_STRING_PARSER = PrimitiveValueReader("String", null, true)
private val RAW_STRING_OR_MAP_PARSER = object : PrimitiveValueReader("Any", null, true) {
  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    out.append("readRawStringOrMap(")
    addReaderParameter(subtyping, out)
    out.append(')')
  }
}

private val JSON_PARSER = RawValueReader()

private val STRING_INT_PAIR_PARSER = StringIntPairValueReader()

internal val VOID_PARSER: ValueReader = object : ValueReader() {
  override fun appendFinishedValueTypeName(out: TextOutput) {
    out.append("void")
  }

  override fun writeReadCode(scope: ClassScope, subtyping: Boolean, out: TextOutput) {
    out.append("null")
  }
}

internal fun createHandler(typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>?>, aClass: Class<*>): TypeWriter<*> {
  val reader = InterfaceReader(typeToTypeHandler)
  reader.processed.addAll(typeToTypeHandler.keys)
  reader.go(arrayOf(aClass))
  return typeToTypeHandler.get(aClass)!!
}

internal class InterfaceReader(val typeToTypeHandler: LinkedHashMap<Class<*>, TypeWriter<*>?>) {
  val processed = THashSet<Class<*>>()
  private val refs = ArrayList<TypeRef<*>>()
  val subtypeCasters = ArrayList<SubtypeCaster>()

  fun go(classes: Array<Class<*>> = typeToTypeHandler.keys.toTypedArray()): LinkedHashMap<Class<*>, TypeWriter<*>?> {
    for (typeClass in classes) {
      createIfNotExists(typeClass)
    }

    var hasUnresolved = true
    while (hasUnresolved) {
      hasUnresolved = false
      // refs can be modified - new items can be added
      for (i in 0..refs.size - 1) {
        val ref: TypeRef<out Any?> = refs.get(i)
        val typeClass: Class<out Any?> = ref.typeClass
        (ref as TypeRef<Any?>).type = typeToTypeHandler.get(typeClass) as TypeWriter<Any?>?
        if (ref.type == null) {
          createIfNotExists(typeClass)
          hasUnresolved = true
          (ref as TypeRef<Any?>).type = typeToTypeHandler.get(typeClass) as TypeWriter<Any?>? ?: throw IllegalStateException()
        }
      }
    }

    for (subtypeCaster in subtypeCasters) {
      subtypeCaster.getSubtypeHandler().subtypeAspect?.setSubtypeCaster(subtypeCaster)
    }

    return typeToTypeHandler
  }

  private fun createIfNotExists(typeClass: Class<*>) {
    if (typeClass == Map::class.java || typeClass == List::class.java || !typeClass.isInterface) {
      return
    }

    if (!processed.add(typeClass)) {
      return
    }

    typeToTypeHandler.put(typeClass, null)

    for (aClass in typeClass.declaredClasses) {
      createIfNotExists(aClass)
    }

    if (!typeClass.isInterface) {
      throw JsonProtocolModelParseException("Json model type should be interface: ${typeClass.name}")
    }

    val fields = FieldProcessor(this, typeClass)
    for (method in fields.methodHandlerMap.keys) {
      val returnType = method.returnType
      if (returnType != typeClass) {
        createIfNotExists(returnType)
      }
    }

    val typeWriter = TypeWriter(typeClass, getSuperclassRef(typeClass), fields.volatileFields, fields.methodHandlerMap, fields.fieldLoaders, fields.lazyRead)
    for (ref in refs) {
      if (ref.typeClass == typeClass) {
        assert(ref.type == null)
        (ref as TypeRef<Any?>).type = typeWriter as TypeWriter<Any?>
        break
      }
    }
    typeToTypeHandler.put(typeClass, typeWriter)
  }

  fun getFieldTypeParser(member: KCallable<*>?, type: Type, isSubtyping: Boolean, method: Method?): ValueReader {
    if (type is Class<*>) {
      @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
      return when {
        type == java.lang.Long.TYPE -> LONG_PARSER
        type == Integer.TYPE || type == Integer::class.java -> INTEGER_PARSER
        type == java.lang.Boolean.TYPE -> BOOLEAN_PARSER
        type == java.lang.Float.TYPE -> FLOAT_PARSER
        type == Number::class.java || type == java.lang.Double.TYPE || type == java.lang.Double::class.java -> NUMBER_PARSER
        type == Void.TYPE -> VOID_PARSER
        type == String::class.java -> {
          if (method != null) {
            val jsonField = member?.annotation<JsonField>()
            if (jsonField != null && jsonField.allowAnyPrimitiveValue) {
              return RAW_STRING_PARSER
            }
            else if ((member?.returnType?.isMarkedNullable ?: false) || method.getAnnotation<Optional>(Optional::class.java) != null) {
              return NULLABLE_STRING_PARSER
            }
          }
          return STRING_PARSER
        }
        type == Any::class.java -> RAW_STRING_OR_MAP_PARSER
        type == JsonReaderEx::class.java -> JSON_PARSER
        type == StringIntPair::class.java -> STRING_INT_PAIR_PARSER
        type.isArray -> ArrayReader(getFieldTypeParser(null, type.componentType, false, null), false)
        type.isEnum -> EnumReader(type as Class<Enum<*>>)
        else -> {
          val ref = getTypeRef(type) ?: throw UnsupportedOperationException("Method return type $type (simple class) not supported")
          ObjectValueReader(ref, isSubtyping, method?.getAnnotation<JsonField>(JsonField::class.java)?.primitiveValue)
        }
      }
    }
    else if (type is ParameterizedType) {
      val isList = type.rawType == List::class.java
      if (isList || type.rawType == Map::class.java) {
        var argumentType = type.actualTypeArguments[if (isList) 0 else 1]
        if (argumentType is WildcardType) {
          val wildcard = argumentType
          if (wildcard.lowerBounds.size == 0 && wildcard.upperBounds.size == 1) {
            argumentType = wildcard.upperBounds[0]
          }
        }
        val componentParser = getFieldTypeParser(null, argumentType, false, method)
        return if (isList) ArrayReader(componentParser, true) else MapReader(componentParser)
      }
      else {
        throw UnsupportedOperationException("Method return type $type (generic) not supported")
      }
    }
    else {
      throw UnsupportedOperationException("Method return type $type not supported")
    }
  }

  fun <T> getTypeRef(typeClass: Class<T>): TypeRef<T>? {
    val result = TypeRef(typeClass)
    refs.add(result)
    return result
  }

  private fun getSuperclassRef(typeClass: Class<*>): TypeRef<*>? {
    var result: TypeRef<*>? = null
    for (interfaceGeneric in typeClass.genericInterfaces) {
      if (interfaceGeneric !is ParameterizedType) {
        continue
      }
      if (interfaceGeneric.rawType != JsonSubtype::class.java) {
        continue
      }
      val param = interfaceGeneric.actualTypeArguments[0]
      if (param !is Class<*>) {
        throw JsonProtocolModelParseException("Unexpected type of superclass $param")
      }
      if (result != null) {
        throw JsonProtocolModelParseException("Already has superclass ${result.typeClass.name}")
      }
      result = getTypeRef(param)
      if (result == null) {
        throw JsonProtocolModelParseException("Unknown base class ${param.name}")
      }
    }
    return result
  }
}