package org.jetbrains.protocolReader

import com.intellij.openapi.util.text.StringUtil
import gnu.trove.THashSet
import org.jetbrains.jsonProtocol.JsonField
import org.jetbrains.jsonProtocol.JsonSubtypeCasting
import org.jetbrains.jsonProtocol.Optional
import org.jetbrains.jsonProtocol.ProtocolName
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType

internal class FieldLoader(val name: String, val jsonName: String, val valueReader: ValueReader, val skipRead: Boolean, val asImpl: Boolean, val defaultValue: String?)

internal fun TextOutput.appendName(loader: FieldLoader): TextOutput {
  if (!loader.asImpl) {
    append(FIELD_PREFIX)
  }
  append(loader.name)
  return this
}

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
    val kClass = typeClass.kotlin
    for (member in  kClass.members) {
      val method = if (member is KProperty<*>) {
        member.javaGetter!!
      }
      else if (member is KFunction<*>) {
        member.javaMethod!!
      }
      else {
        continue
      }

      val methodClass = method.declaringClass
      // use method from super if super located in the same package
      if (methodClass != typeClass) {
        val methodPackage = methodClass.`package`
        if (methodPackage != classPackage && !classPackage.name.startsWith("${methodPackage.name}.")) {
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
          methodHandler = createMethodHandler(member, method, skippedNames.contains(method.name)) ?: continue
        }
        else {
          methodHandler = processManualSubtypeMethod(member, method, jsonSubtypeCaseAnnotation)
          lazyRead = true
        }
        methodHandlerMap.put(method, methodHandler)
      }
      catch (e: Exception) {
        throw JsonProtocolModelParseException("Problem with method $method", e)
      }
    }
  }

  private fun createMethodHandler(member: KCallable<*>, method: Method, skipRead: Boolean): MethodHandler? {
    var protocolName = member.annotation<ProtocolName>()?.name ?: member.name
    val genericReturnType = member.returnType.javaType
    val isNotNull: Boolean
    val isPrimitive = if (genericReturnType is Class<*>) genericReturnType.isPrimitive else genericReturnType !is ParameterizedType
    val optionalAnnotation = member.annotation<Optional>()
    if (isPrimitive || optionalAnnotation != null) {
      isNotNull = false
    }
    else {
      val fieldAnnotation = member.annotation<JsonField>()
      if (fieldAnnotation == null) {
        isNotNull = !member.returnType.isMarkedNullable
      }
      else {
        isNotNull = !fieldAnnotation.allowAnyPrimitiveValue && !fieldAnnotation.allowAnyPrimitiveValueAndMap
      }
    }

    val fieldTypeParser = reader.getFieldTypeParser(member, genericReturnType, false, method)
    val isProperty = member is KProperty<*>
    val isAsImpl = isProperty && !isNotNull
    if (fieldTypeParser != VOID_PARSER) {
      fieldLoaders.add(FieldLoader(member.name, protocolName, fieldTypeParser, skipRead, isAsImpl, StringUtil.nullize(optionalAnnotation?.default)))
    }

    if (isAsImpl) {
      return null
    }

    val effectiveFieldName = if (fieldTypeParser == VOID_PARSER) null else member.name
    return object : MethodHandler {
      override fun writeMethodImplementationJava(scope: ClassScope, method: Method, out: TextOutput) {
        out.append("override ").append(if (isProperty) "val" else "fun")
        out.append(" ").appendEscapedName(method.name)

        if (isProperty) {
          out.newLine()
          out.indentIn()
          out.append("get()")
          // todo append type name
        }
        else {
          out.append("()")
        }

        if (effectiveFieldName == null) {
          out.openBlock()
          out.closeBlock()
        }
        else {
          out.append(" = ").append(FIELD_PREFIX).append(effectiveFieldName)
          if (isNotNull) {
            out.append("!!")
          }

          if (isProperty) {
            out.indentOut()
          }
        }
      }
    }
  }

  private fun processManualSubtypeMethod(member: KCallable<*>, m: Method, jsonSubtypeCaseAnn: JsonSubtypeCasting): MethodHandler {
    val fieldTypeParser = reader.getFieldTypeParser(member, m.genericReturnType, !jsonSubtypeCaseAnn.reinterpret, null)
    val fieldInfo = allocateVolatileField(fieldTypeParser, true)
    val handler = LazyCachedMethodHandler(fieldTypeParser, fieldInfo)
    val parserAsObjectValueParser = fieldTypeParser.asJsonTypeParser()
    if (parserAsObjectValueParser != null && parserAsObjectValueParser.isSubtyping()) {
      reader.subtypeCasters.add(object : SubtypeCaster(parserAsObjectValueParser.type) {
        override fun writeJava(out: TextOutput) {
          out.append(m.name).append("()")
        }
      })
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

internal inline fun <reified T : Annotation> KCallable<*>.annotation(): T? = annotations.firstOrNull() { it is T } as? T ?: (this as? KFunction<*>)?.javaMethod?.getAnnotation<T>(T::class.java)

/**
 * An internal facility for navigating from object of base type to object of subtype. Used only
 * when user wants to parse JSON object as subtype.
 */
internal abstract class SubtypeCaster(private val subtypeRef: TypeRef<*>) {
  abstract fun writeJava(out: TextOutput)

  fun getSubtypeHandler() = subtypeRef.type!!
}