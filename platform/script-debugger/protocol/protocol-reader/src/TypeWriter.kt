package org.jetbrains.protocolReader

import org.jetbrains.jsonProtocol.JsonObjectBased
import java.lang.reflect.Method
import java.util.*

internal val FIELD_PREFIX = '_'

internal val NAME_VAR_NAME = "_n"

private fun assignField(out: TextOutput, fieldName: String) = out.append(FIELD_PREFIX).append(fieldName).append(" = ")

internal class TypeRef<T>(val typeClass: Class<T>) {
  var type: TypeWriter<T>? = null
}

internal class TypeWriter<T>(val typeClass: Class<T>, jsonSuperClass: TypeRef<*>?, private val volatileFields: List<VolatileFieldBinding>, private val methodHandlerMap: LinkedHashMap<Method, MethodHandler>,
                             /** Loaders that should read values and save them in field array on parse time. */
                             private val fieldLoaders: List<FieldLoader>, private val hasLazyFields: Boolean) {

  /** Subtype aspects of the type or null */
  val subtypeAspect = if (jsonSuperClass == null) null else ExistingSubtypeAspect(jsonSuperClass)

  fun writeInstantiateCode(scope: ClassScope, out: TextOutput) {
    writeInstantiateCode(scope, false, out)
  }

  fun writeInstantiateCode(scope: ClassScope, deferredReading: Boolean, out: TextOutput) {
    val className = scope.getTypeImplReference(this)
    if (deferredReading || subtypeAspect == null) {
      out.append(className)
    }
    else {
      subtypeAspect.writeInstantiateCode(className, out)
    }
  }

  fun write(fileScope: FileScope) {
    val out = fileScope.output
    val valueImplClassName = fileScope.getTypeImplShortName(this)
    out.append("private class ").append(valueImplClassName).append('(').append(JSON_READER_PARAMETER_DEF).comma().append("preReadName: String?")
    subtypeAspect?.writeSuperFieldJava(out)
    out.append(") : ").append(typeClass.canonicalName).openBlock()

    if (hasLazyFields || JsonObjectBased::class.java.isAssignableFrom(typeClass)) {
      out.append("private var ").append(PENDING_INPUT_READER_NAME).append(": ").append(JSON_READER_CLASS_NAME).append("? = reader.subReader()!!").newLine()
    }

    val classScope = fileScope.newClassScope()
    for (field in volatileFields) {
      field.writeFieldDeclaration(classScope, out)
      out.newLine()
    }

    for (loader in fieldLoaders) {
      if (loader.asImpl) {
        out.append("override")
      }
      else {
        out.append("private")
      }
      out.append(" var ").appendName(loader)

      fun addType() {
        out.append(": ")
        loader.valueReader.appendFinishedValueTypeName(out)
        out.append("? = null")
      }

      if (loader.valueReader is PrimitiveValueReader) {
        val defaultValue = loader.defaultValue ?: loader.valueReader.defaultValue
        if (defaultValue != null) {
          out.append(" = ").append(defaultValue)
        }
        else {
          addType()
        }
      }
      else {
        addType()
      }
      out.newLine()
    }

    if (fieldLoaders.isNotEmpty()) {
      out.newLine()
    }
    writeConstructorMethod(classScope, out)
    out.newLine()

    subtypeAspect?.writeParseMethod(classScope, out)

    for ((key, value) in methodHandlerMap.entries) {
      out.newLine()
      value.writeMethodImplementationJava(classScope, key, out)
      out.newLine()
    }

    writeBaseMethods(out)
    subtypeAspect?.writeGetSuperMethodJava(out)

    writeEqualsMethod(valueImplClassName, out)

    out.indentOut().append('}')
  }

  /**
   * Generates Java implementation of standard methods of JSON type class (if needed):
   * {@link org.jetbrains.jsonProtocol.JsonObjectBased#getDeferredReader()}
   */
  private fun writeBaseMethods(out: TextOutput) {
    val method: Method
    try {
      method = typeClass.getMethod("getDeferredReader")
    }
    catch (ignored: NoSuchMethodException) {
      // Method not found, skip.
      return
    }

    out.newLine()
    writeMethodDeclarationJava(out, method)
    out.append(" = ").append(PENDING_INPUT_READER_NAME)
  }

  private fun writeEqualsMethod(valueImplClassName: String, out: TextOutput) {
    if (fieldLoaders.isEmpty()) {
      return
    }

    out.newLine().append("override fun equals(other: Any?): Boolean = ")
    out.append("other is ").append(valueImplClassName)

    // at first we should compare primitive values, then enums, then string, then objects
    fun fieldWeight(reader: ValueReader): Int {
      var w = 10
      if (reader is PrimitiveValueReader) {
        w--
        if (reader.className != "String") {
          w--
        }
      }
      else if (reader is EnumReader) {
        // -1 as primitive, -1 as not a string
        w -= 2
      }
      return w
    }

    for (loader in fieldLoaders.sortedWith(Comparator<FieldLoader> { f1, f2 -> fieldWeight((f1.valueReader)) - fieldWeight((f2.valueReader))})) {
      out.append(" && ")
      out.appendName(loader).append(" == ").append("other.").appendName(loader)
    }
    out.newLine()
  }

  private fun writeConstructorMethod(classScope: ClassScope, out: TextOutput) {
    out.append("init").block {
      if (fieldLoaders.isEmpty()) {
        out.append(READER_NAME).append(".skipValue()")
      }
      else {
        out.append("var ").append(NAME_VAR_NAME).append(" = preReadName")
        out.newLine().append("if (").append(NAME_VAR_NAME).append(" == null && reader.hasNext() && reader.beginObject().hasNext())").block {
          out.append(NAME_VAR_NAME).append(" = reader.nextName()")
        }
        out.newLine()

        writeReadFields(out, classScope)

        // we don't read all data if we have lazy fields, so, we should not check end of stream
        //if (!hasLazyFields) {
        out.newLine().newLine().append(READER_NAME).append(".endObject()")
        //}
      }
    }
  }

  private fun writeReadFields(out: TextOutput, classScope: ClassScope) {
    val stopIfAllFieldsWereRead = hasLazyFields
    val hasOnlyOneFieldLoader = fieldLoaders.size == 1
    val isTracedStop = stopIfAllFieldsWereRead && !hasOnlyOneFieldLoader
    if (isTracedStop) {
      out.newLine().append("var i = 0")
    }

    out.newLine().append("loop@ while (").append(NAME_VAR_NAME).append(" != null)").block {
      (out + "when (" + NAME_VAR_NAME + ")").block {
        var isFirst = true
        for (fieldLoader in fieldLoaders) {
          if (fieldLoader.skipRead) {
            continue
          }

          if (!isFirst) {
            out.newLine()
          }

          out.append('"')
          if (fieldLoader.jsonName.first() == '$') {
            out.append('\\')
          }
          out.append(fieldLoader.jsonName).append('"').append(" -> ")

          if (stopIfAllFieldsWereRead && !isTracedStop) {
            out.openBlock()
          }

          val primitiveValueName = if (fieldLoader.valueReader is ObjectValueReader) fieldLoader.valueReader.primitiveValueName else null
          if (primitiveValueName != null) {
            out.append("if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT)").openBlock()
          }
          out.appendName(fieldLoader).append(" = ")

          fieldLoader.valueReader.writeReadCode(classScope, false, out)

          if (primitiveValueName != null) {
            out.newLine().append("else").openBlock()

            assignField(out, "${primitiveValueName}Type")
            out.append("reader.peek()").newLine()

            assignField(out, primitiveValueName)
            out + "reader.nextString(true)"
          }

          if (stopIfAllFieldsWereRead && !isTracedStop) {
            out.newLine().append(READER_NAME).append(".skipValues()").newLine().append("break@loop").closeBlock()
          }

          if (isFirst) {
            isFirst = false
          }
        }

        out.newLine().append("else ->")
        if (isTracedStop) {
          out.block {
            out.append("reader.skipValue()")
            out.newLine() + NAME_VAR_NAME + " = reader.nextNameOrNull()"
            out.newLine() + "continue@loop"
          }
        }
        else {
          out.space().append("reader.skipValue()")
        }
      }

      out.newLine() + NAME_VAR_NAME + " = reader.nextNameOrNull()"

      if (isTracedStop) {
        out.newLine().newLine().append("if (i++ == ").append(fieldLoaders.size - 1).append(")").block {
          (out + READER_NAME + ".skipValues()").newLine() + "break"
        }
      }
    }
  }
}