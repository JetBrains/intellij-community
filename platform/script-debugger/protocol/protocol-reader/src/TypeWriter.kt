package org.jetbrains.protocolReader

import org.jetbrains.jsonProtocol.JsonObjectBased

import java.lang.reflect.Method
import java.util.LinkedHashMap

public val FIELD_PREFIX: Char = '_'

private fun assignField(out: TextOutput, fieldName: String): TextOutput {
  return out.append(FIELD_PREFIX).append(fieldName).append(" = ")
}

class TypeWriter<T>(val typeClass: Class<T>, jsonSuperClass: TypeRef<*>?, private val volatileFields: List<VolatileFieldBinding>, private val methodHandlerMap: LinkedHashMap<Method, MethodHandler>,
                    /** Loaders that should read values and save them in field array on parse time. */
                    private val fieldLoaders: List<FieldLoader>, private val hasLazyFields: Boolean) {

  /** Subtype aspects of the type or null */
  val subtypeAspect: ExistingSubtypeAspect?

  init {
    subtypeAspect = if (jsonSuperClass == null) null else ExistingSubtypeAspect(jsonSuperClass)
  }

  public fun writeInstantiateCode(scope: ClassScope, out: TextOutput) {
    writeInstantiateCode(scope, false, out)
  }

  public fun writeInstantiateCode(scope: ClassScope, deferredReading: Boolean, out: TextOutput) {
    val className = scope.getTypeImplReference(this)
    if (deferredReading || subtypeAspect == null) {
      out.append("new ").append(className)
    }
    else {
      subtypeAspect.writeInstantiateCode(className, out)
    }
  }

  public fun write(fileScope: FileScope) {
    val out = fileScope.output
    val valueImplClassName = fileScope.getTypeImplShortName(this)
    out.append("private static final class ").append(valueImplClassName)

    out.append(" implements ").append(typeClass.getCanonicalName()).openBlock()

    if (hasLazyFields || javaClass<JsonObjectBased>().isAssignableFrom(typeClass)) {
      out.append("private ").append(JSON_READER_CLASS_NAME).space().append(PENDING_INPUT_READER_NAME).semi().newLine()
    }

    val classScope = fileScope.newClassScope()
    for (field in volatileFields) {
      field.writeFieldDeclaration(classScope, out)
      out.newLine()
    }

    for (loader in fieldLoaders) {
      out.append("private").space()
      loader.valueReader.appendFinishedValueTypeName(out)
      out.space().append(FIELD_PREFIX).append(loader.name)
      if (loader.valueReader is PrimitiveValueReader) {
        val defaultValue = loader.valueReader.defaultValue
        if (defaultValue != null) {
          out.append(" = ").append(defaultValue)
        }
      }
      out.semi()
      out.newLine()
    }

    if (subtypeAspect != null) {
      subtypeAspect.writeSuperFieldJava(out)
    }

    writeConstructorMethod(valueImplClassName, classScope, out)
    out.newLine()

    if (subtypeAspect != null) {
      subtypeAspect.writeParseMethod(valueImplClassName, classScope, out)
    }

    for (entry in methodHandlerMap.entrySet()) {
      out.newLine()
      entry.getValue().writeMethodImplementationJava(classScope, entry.getKey(), out)
      out.newLine()
    }

    writeBaseMethods(out)
    if (subtypeAspect != null) {
      subtypeAspect.writeGetSuperMethodJava(out)
    }
    out.indentOut().append('}')
  }

  /**
   * Generates Java implementation of standard methods of JSON type class (if needed):
   * {@link org.jetbrains.jsonProtocol.JsonObjectBased#getDeferredReader()}
   */
  private fun writeBaseMethods(out: TextOutput) {
    val typeClass = this.typeClass
    val method: Method
    try {
      method = typeClass.getMethod("getDeferredReader")
    }
    catch (e: SecurityException) {
      throw RuntimeException(e)
    }
    catch (ignored: NoSuchMethodException) {
      // Method not found, skip.
      return
    }


    out.newLine()
    writeMethodDeclarationJava(out, method)
    out.openBlock()
    out.append("return ").append(PENDING_INPUT_READER_NAME).semi()
    out.closeBlock()
  }

  private fun writeConstructorMethod(valueImplClassName: String, classScope: ClassScope, out: TextOutput) {
    out.newLine().append(valueImplClassName).append('(').append(JSON_READER_PARAMETER_DEF).comma().append("String name")
    if (subtypeAspect != null) {
      subtypeAspect.writeSuperConstructorParamJava(out)
    }
    out.append(')').openBlock()

    if (subtypeAspect != null) {
      subtypeAspect.writeSuperConstructorInitialization(out)
    }

    if (javaClass<JsonObjectBased>().isAssignableFrom(typeClass) || hasLazyFields) {
      out.append(PENDING_INPUT_READER_NAME).append(" = ").append(READER_NAME).append(".subReader()").semi().newLine()
    }

    if (fieldLoaders.isEmpty()) {
      out.append(READER_NAME).append(".skipValue()").semi()
    }
    else {
      out.append("if (name == null)").openBlock()
      run {
        out.append("if (reader.hasNext() && reader.beginObject().hasNext())").openBlock()
        run { out.append("name = reader.nextName()").semi() }
        out.closeBlock()
        out.newLine().append("else").openBlock()
        run { out.append("return").semi() }
        out.closeBlock()
      }
      out.closeBlock()
      out.newLine()

      writeReadFields(out, classScope)

      // we don't read all data if we have lazy fields, so, we should not check end of stream
      //if (!hasLazyFields) {
      out.newLine().newLine().append(READER_NAME).append(".endObject()").semi()
      //}
    }
    out.closeBlock()
  }

  private fun writeReadFields(out: TextOutput, classScope: ClassScope) {
    val stopIfAllFieldsWereRead = hasLazyFields
    val hasOnlyOneFieldLoader = fieldLoaders.size() == 1
    val isTracedStop = stopIfAllFieldsWereRead && !hasOnlyOneFieldLoader
    if (isTracedStop) {
      out.newLine().append("int i = 0").semi()
    }

    out.newLine().append("do").openBlock()
    var isFirst = true
    var operator = "if"
    for (fieldLoader in fieldLoaders) {
      if (fieldLoader.skipRead) {
        continue
      }

      if (!isFirst) {
        out.newLine()
      }

      out.append(operator).append(" (name")
      out.append(".equals(\"").append(fieldLoader.jsonName).append("\"))").openBlock()
      run {
        val primitiveValueName = if (fieldLoader.valueReader is ObjectValueReader) fieldLoader.valueReader.primitiveValueName else null
        if (primitiveValueName != null) {
          out.append("if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT)").openBlock()
        }
        assignField(out, fieldLoader.name)

        fieldLoader.valueReader.writeReadCode(classScope, false, out)
        out.semi()

        if (primitiveValueName != null) {
          out.closeBlock()
          out.newLine().append("else").openBlock()

          assignField(out, primitiveValueName + "Type")
          out.append("reader.peek()").semi().newLine()

          assignField(out, primitiveValueName)
          out.append("reader.nextString(true)").semi()

          out.closeBlock()
        }

        if (stopIfAllFieldsWereRead && !isTracedStop) {
          out.newLine().append(READER_NAME).append(".skipValues()").semi().newLine().append("break").semi()
        }
      }
      out.closeBlock()

      if (isFirst) {
        isFirst = false
        operator = "else if"
      }
    }

    out.newLine().append("else").openBlock().append("reader.skipValue();")
    if (isTracedStop) {
      out.newLine().append("continue").semi()
    }
    out.closeBlock()
    if (isTracedStop) {
      out.newLine().newLine().append("if (i == ").append(fieldLoaders.size() - 1).append(")").openBlock()
      out.append(READER_NAME).append(".skipValues()").semi().newLine().append("break").semi().closeBlock()
      out.newLine().append("else").openBlock().append("i++").semi().closeBlock()
    }
    out.closeBlock()
    out.newLine().append("while ((name = reader.nextNameOrNull()) != null)").semi()
  }
}