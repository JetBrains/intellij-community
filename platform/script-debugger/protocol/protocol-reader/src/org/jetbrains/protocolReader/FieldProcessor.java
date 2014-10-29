package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.JsonField;
import org.jetbrains.jsonProtocol.JsonNullable;
import org.jetbrains.jsonProtocol.JsonOptionalField;
import org.jetbrains.jsonProtocol.JsonSubtypeCasting;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

final class FieldProcessor<T> {
  private final List<FieldLoader> fieldLoaders = new ArrayList<>(2);
  private final LinkedHashMap<Method, MethodHandler> methodHandlerMap = new LinkedHashMap<>();
  private final List<VolatileFieldBinding> volatileFields = new ArrayList<>(2);
  boolean lazyRead;
  private final InterfaceReader reader;

  FieldProcessor(@NotNull InterfaceReader reader, @NotNull Class<T> typeClass) {
    this.reader = reader;

    Method[] methods = typeClass.getMethods();
    // todo sort by source location
    Arrays.sort(methods, new Comparator<Method>() {
      @Override
      public int compare(@NotNull Method o1, @NotNull Method o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    Package aPackage = typeClass.getPackage();
    for (Method method : methods) {
      Class<?> methodClass = method.getDeclaringClass();
      // use method from super if super located in the same package
      if (methodClass != typeClass && methodClass.getPackage() != aPackage) {
        continue;
      }

      if (method.getParameterTypes().length != 0) {
        throw new JsonProtocolModelParseException("No parameters expected in " + method);
      }

      try {
        String fieldName = checkAndGetJsonFieldName(method);
        MethodHandler methodHandler;

        JsonSubtypeCasting jsonSubtypeCaseAnnotation = method.getAnnotation(JsonSubtypeCasting.class);
        if (jsonSubtypeCaseAnnotation != null) {
          methodHandler = processManualSubtypeMethod(method, jsonSubtypeCaseAnnotation);
          lazyRead = true;
        }
        else {
          methodHandler = processFieldGetterMethod(method, fieldName);
        }
        methodHandlerMap.put(method, methodHandler);
      }
      catch (JsonProtocolModelParseException e) {
        throw new JsonProtocolModelParseException("Problem with method " + method, e);
      }
    }
  }

  private MethodHandler processFieldGetterMethod(@NotNull Method method, @NotNull String fieldName) {
    Type genericReturnType = method.getGenericReturnType();
    boolean nullable;
    if (method.getAnnotation(JsonNullable.class) != null) {
      nullable = true;
    }
    else if (genericReturnType == String.class || genericReturnType == Enum.class) {
      JsonField jsonField = method.getAnnotation(JsonField.class);
      if (jsonField != null) {
        nullable = jsonField.optional() && !jsonField.allowAnyPrimitiveValue() && !jsonField.allowAnyPrimitiveValueAndMap();
      }
      else {
        nullable = method.getAnnotation(JsonOptionalField.class) != null;
      }
    }
    else {
      nullable = false;
    }

    ValueReader fieldTypeParser = reader.getFieldTypeParser(genericReturnType, nullable, false, method);
    if (fieldTypeParser != InterfaceReader.VOID_PARSER) {
      fieldLoaders.add(new FieldLoader(fieldName, fieldTypeParser));
    }

    final String effectiveFieldName = fieldTypeParser == InterfaceReader.VOID_PARSER ? null : fieldName;
    return new MethodHandler() {
      @Override
      void writeMethodImplementationJava(@NotNull ClassScope scope, @NotNull Method method, @NotNull TextOutput out) {
        if (!nullable) {
          out.append("@NotNull").newLine();
        }
        writeMethodDeclarationJava(out, method);
        out.openBlock();
        if (effectiveFieldName != null) {
          out.append("return ").append(FieldLoader.FIELD_PREFIX).append(effectiveFieldName).semi();
        }
        out.closeBlock();
      }
    };
  }

  private MethodHandler processManualSubtypeMethod(final Method m, JsonSubtypeCasting jsonSubtypeCaseAnn) {
    ValueReader fieldTypeParser = reader.getFieldTypeParser(m.getGenericReturnType(), false, !jsonSubtypeCaseAnn.reinterpret(), null);
    VolatileFieldBinding fieldInfo = allocateVolatileField(fieldTypeParser, true);
    LazyCachedMethodHandler handler = new LazyCachedMethodHandler(fieldTypeParser, fieldInfo);
    ObjectValueReader<?> parserAsObjectValueParser = fieldTypeParser.asJsonTypeParser();
    if (parserAsObjectValueParser != null && parserAsObjectValueParser.isSubtyping()) {
      SubtypeCaster subtypeCaster = new SubtypeCaster(parserAsObjectValueParser.getType()) {
        @Override
        void writeJava(TextOutput out) {
          out.append(m.getName()).append("()");
        }
      };
      reader.subtypeCasters.add(subtypeCaster);
    }
    return handler;
  }

  List<VolatileFieldBinding> getVolatileFields() {
    return volatileFields;
  }

  List<FieldLoader> getFieldLoaders() {
    return fieldLoaders;
  }

  LinkedHashMap<Method, MethodHandler> getMethodHandlerMap() {
    return methodHandlerMap;
  }

  private VolatileFieldBinding allocateVolatileField(final ValueReader fieldTypeParser, boolean internalType) {
    int position = volatileFields.size();
    FieldTypeInfo fieldTypeInfo;
    if (internalType) {
      fieldTypeInfo = fieldTypeParser::appendInternalValueTypeName;
    }
    else {
      fieldTypeInfo = (scope, out) -> fieldTypeParser.appendFinishedValueTypeName(out);
    }
    VolatileFieldBinding binding = new VolatileFieldBinding(position, fieldTypeInfo);
    volatileFields.add(binding);
    return binding;
  }

  @NotNull
  private static String checkAndGetJsonFieldName(@NotNull Method method) {
    if (method.getParameterTypes().length != 0) {
      throw new JsonProtocolModelParseException("Must have 0 parameters");
    }
    JsonField fieldAnnotation = method.getAnnotation(JsonField.class);
    if (fieldAnnotation != null) {
      String jsonLiteralName = fieldAnnotation.jsonLiteralName();
      if (!jsonLiteralName.isEmpty()) {
        return jsonLiteralName;
      }
    }
    return method.getName();
  }
}
