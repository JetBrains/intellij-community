package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.JsonObjectBased;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TypeWriter<T> {
  public static final char FIELD_PREFIX = '_';

  final Class<T> typeClass;

  private final List<VolatileFieldBinding> volatileFields;

  private final LinkedHashMap<Method, MethodHandler> methodHandlerMap;

  /** Loaders that should read values and save them in field array on parse time. */
  private final List<FieldLoader> fieldLoaders;

  /** Subtype aspects of the type or null */
  final ExistingSubtypeAspect subtypeAspect;

  private final boolean hasLazyFields;

  TypeWriter(Class<T> typeClass, TypeRef<?> jsonSuperClass,
             List<VolatileFieldBinding> volatileFields,
             LinkedHashMap<Method, MethodHandler> methodHandlerMap,
             List<FieldLoader> fieldLoaders,
             boolean hasLazyFields) {
    this.typeClass = typeClass;
    this.volatileFields = volatileFields;
    this.methodHandlerMap = methodHandlerMap;
    this.fieldLoaders = fieldLoaders;
    this.hasLazyFields = hasLazyFields;
    subtypeAspect = jsonSuperClass == null ? null : new ExistingSubtypeAspect(jsonSuperClass);
  }

  public void writeInstantiateCode(@NotNull ClassScope scope, @NotNull TextOutput out) {
    writeInstantiateCode(scope, false, out);
  }

  public void writeInstantiateCode(@NotNull ClassScope scope, boolean deferredReading, @NotNull TextOutput out) {
    String className = scope.getTypeImplReference(this);
    if (deferredReading || subtypeAspect == null) {
      out.append("new ").append(className);
    }
    else {
      subtypeAspect.writeInstantiateCode(className, out);
    }
  }

  public void write(@NotNull FileScope fileScope) {
    TextOutput out = fileScope.getOutput();
    String valueImplClassName = fileScope.getTypeImplShortName(this);
    out.append("private static final class ").append(valueImplClassName);

    out.append(" implements ").append(typeClass.getCanonicalName()).openBlock();

    if (hasLazyFields || JsonObjectBased.class.isAssignableFrom(typeClass)) {
      out.append("private ").append(Util.JSON_READER_CLASS_NAME).space().append(Util.PENDING_INPUT_READER_NAME).semi().newLine();
    }

    ClassScope classScope = fileScope.newClassScope();
    for (VolatileFieldBinding field : volatileFields) {
      field.writeFieldDeclaration(classScope, out);
      out.newLine();
    }

    for (FieldLoader loader : fieldLoaders) {
      out.append("private").space();
      loader.valueReader.appendFinishedValueTypeName(out);
      out.space().append(FIELD_PREFIX).append(loader.name);
      if (loader.valueReader instanceof PrimitiveValueReader) {
        String defaultValue = ((PrimitiveValueReader)loader.valueReader).defaultValue;
        if (defaultValue != null) {
          out.append(" = ").append(defaultValue);
        }
      }
      out.semi();
      out.newLine();
    }

    if (subtypeAspect != null) {
      subtypeAspect.writeSuperFieldJava(out);
    }

    writeConstructorMethod(valueImplClassName, classScope, out);
    out.newLine();

    if (subtypeAspect != null) {
      subtypeAspect.writeParseMethod(valueImplClassName, classScope, out);
    }

    for (Map.Entry<Method, MethodHandler> entry : methodHandlerMap.entrySet()) {
      out.newLine();
      entry.getValue().writeMethodImplementationJava(classScope, entry.getKey(), out);
      out.newLine();
    }

    writeBaseMethods(out);
    if (subtypeAspect != null) {
      subtypeAspect.writeGetSuperMethodJava(out);
    }
    out.indentOut().append('}');
  }

  /**
   * Generates Java implementation of standard methods of JSON type class (if needed):
   * {@link org.jetbrains.jsonProtocol.JsonObjectBased#getDeferredReader()}
   */
  private void writeBaseMethods(TextOutput out) {
    Class<?> typeClass = this.typeClass;
    Method method;
    try {
      method = typeClass.getMethod("getDeferredReader");
    }
    catch (SecurityException e) {
      throw new RuntimeException(e);
    }
    catch (NoSuchMethodException ignored) {
      // Method not found, skip.
      return;
    }

    out.newLine();
    MethodHandler.writeMethodDeclarationJava(out, method);
    out.openBlock();
    out.append("return ").append(Util.PENDING_INPUT_READER_NAME).semi();
    out.closeBlock();
  }

  private void writeConstructorMethod(@NotNull String valueImplClassName, @NotNull ClassScope classScope, @NotNull TextOutput out) {
    out.newLine().append(valueImplClassName).append('(').append(Util.JSON_READER_PARAMETER_DEF).comma().append("String name");
    if (subtypeAspect != null) {
      subtypeAspect.writeSuperConstructorParamJava(out);
    }
    out.append(')').openBlock();

    if (subtypeAspect != null) {
      subtypeAspect.writeSuperConstructorInitialization(out);
    }

    if (JsonObjectBased.class.isAssignableFrom(typeClass) || hasLazyFields) {
      out.append(Util.PENDING_INPUT_READER_NAME).append(" = ").append(Util.READER_NAME).append(".subReader()").semi().newLine();
    }

    if (fieldLoaders.isEmpty()) {
      out.append(Util.READER_NAME).append(".skipValue()").semi();
    }
    else {
      out.append("if (name == null)").openBlock();
      {
        out.append("if (reader.hasNext() && reader.beginObject().hasNext())").openBlock();
        {
          out.append("name = reader.nextName()").semi();
        }
        out.closeBlock();
        out.newLine().append("else").openBlock();
        {
          out.append("return").semi();
        }
        out.closeBlock();
      }
      out.closeBlock();
      out.newLine();

      writeReadFields(out, classScope);

      // we don't read all data if we have lazy fields, so, we should not check end of stream
      //if (!hasLazyFields) {
        out.newLine().newLine().append(Util.READER_NAME).append(".endObject()").semi();
      //}
    }
    out.closeBlock();
  }

  private void writeReadFields(TextOutput out, ClassScope classScope) {
    boolean stopIfAllFieldsWereRead = hasLazyFields;
    boolean hasOnlyOneFieldLoader = fieldLoaders.size() == 1;
    boolean isTracedStop = stopIfAllFieldsWereRead && !hasOnlyOneFieldLoader;
    if (isTracedStop) {
      out.newLine().append("int i = 0").semi();
    }

    out.newLine().append("do").openBlock();
    boolean isFirst = true;
    String operator = "if";
    for (FieldLoader fieldLoader : fieldLoaders) {
      if (fieldLoader.skipRead) {
        continue;
      }

      if (!isFirst) {
        out.newLine();
      }

      out.append(operator).append(" (name");
      out.append(".equals(\"").append(fieldLoader.jsonName).append("\"))").openBlock();
      {
        String primitiveValueName = fieldLoader.valueReader instanceof ObjectValueReader ? ((ObjectValueReader)fieldLoader.valueReader).primitiveValueName : null;
        if (primitiveValueName != null) {
          out.append("if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT)").openBlock();
        }
        assignField(out, fieldLoader.name);

        fieldLoader.valueReader.writeReadCode(classScope, false, out);
        out.semi();

        if (primitiveValueName != null) {
          out.closeBlock();
          out.newLine().append("else").openBlock();

          assignField(out, primitiveValueName + "Type");
          out.append("reader.peek()").semi().newLine();

          assignField(out, primitiveValueName);
          out.append("reader.nextString(true)").semi();

          out.closeBlock();
        }

        if (stopIfAllFieldsWereRead && !isTracedStop) {
          out.newLine().append(Util.READER_NAME).append(".skipValues()").semi().newLine().append("break").semi();
        }
      }
      out.closeBlock();

      if (isFirst) {
        isFirst = false;
        operator = "else if";
      }
    }

    out.newLine().append("else").openBlock().append("reader.skipValue();");
    if (isTracedStop) {
      out.newLine().append("continue").semi();
    }
    out.closeBlock();
    if (isTracedStop) {
      out.newLine().newLine().append("if (i == ").append(fieldLoaders.size() - 1).append(")").openBlock();
      out.append(Util.READER_NAME).append(".skipValues()").semi().newLine().append("break").semi().closeBlock();
      out.newLine().append("else").openBlock().append("i++").semi().closeBlock();
    }
    out.closeBlock();
    out.newLine().append("while ((name = reader.nextNameOrNull()) != null)").semi();
  }

  @NotNull
  private static TextOutput assignField(TextOutput out, String fieldName) {
    return out.append(FIELD_PREFIX).append(fieldName).append(" = ");
  }
}