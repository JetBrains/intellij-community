package org.jetbrains.protocolReader;

import org.chromium.protocolReader.*;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

class FieldProcessor<T> {
  private final Class<T> typeClass;

  private final List<FieldLoader> fieldLoaders = new ArrayList<>(2);
  private final LinkedHashMap<Method, MethodHandler> methodHandlerMap = new LinkedHashMap<>();
  private final List<VolatileFieldBinding> volatileFields = new ArrayList<>(2);
  boolean lazyRead;
  private final InterfaceReader reader;

  FieldProcessor(InterfaceReader reader, Class<T> typeClass) {
    this.typeClass = typeClass;
    this.reader = reader;
  }

  void go() {
    Method[] methods = typeClass.getDeclaredMethods();
    // todo sort by source location
    Arrays.sort(methods, new Comparator<Method>() {
      @Override
      public int compare(Method o1, Method o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    for (Method m : methods) {
      try {
        processMethod(m);
      }
      catch (JsonProtocolModelParseException e) {
        throw new JsonProtocolModelParseException("Problem with method " + m, e);
      }
    }
  }

  private void processMethod(Method m) {
    if (m.getParameterTypes().length != 0) {
      throw new JsonProtocolModelParseException("No parameters expected in " + m);
    }
    String fieldName = checkAndGetJsonFieldName(m);
    MethodHandler methodHandler;

    JsonSubtypeCasting jsonSubtypeCaseAnnotation = m.getAnnotation(JsonSubtypeCasting.class);
    if (jsonSubtypeCaseAnnotation != null) {
      methodHandler = processManualSubtypeMethod(m, jsonSubtypeCaseAnnotation);
      lazyRead = true;
    }
    else {
      methodHandler = processFieldGetterMethod(m, fieldName);
    }
    methodHandlerMap.put(m, methodHandler);
  }

  private MethodHandler processFieldGetterMethod(Method m, String fieldName) {
    Type genericReturnType = m.getGenericReturnType();
    boolean nullable;
    if (m.getAnnotation(JsonNullable.class) != null) {
      nullable = true;
    }
    else if (genericReturnType == String.class || genericReturnType == Enum.class) {
      JsonField jsonField = m.getAnnotation(JsonField.class);
      if (jsonField != null) {
        nullable = jsonField.optional() && !jsonField.allowAnyPrimitiveValue() && !jsonField.allowAnyPrimitiveValueAndMap();
      }
      else {
        nullable = m.getAnnotation(JsonOptionalField.class) != null;
      }
    }
    else {
      nullable = false;
    }

    ValueReader fieldTypeParser = reader.getFieldTypeParser(genericReturnType, nullable, false, m);
    if (fieldTypeParser != InterfaceReader.VOID_PARSER) {
      fieldLoaders.add(new FieldLoader(fieldName, fieldTypeParser));
    }
    return new PreparsedFieldMethodHandler(fieldTypeParser == InterfaceReader.VOID_PARSER ? null : fieldName);
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
      fieldTypeInfo = new FieldTypeInfo() {
        @Override
        public void appendValueTypeNameJava(FileScope scope, TextOutput out) {
          fieldTypeParser.appendInternalValueTypeName(scope, out);
        }
      };
    }
    else {
      fieldTypeInfo = new FieldTypeInfo() {
        @Override
        public void appendValueTypeNameJava(FileScope scope, TextOutput out) {
          fieldTypeParser.appendFinishedValueTypeName(out);
        }
      };
    }
    VolatileFieldBinding binding = new VolatileFieldBinding(position, fieldTypeInfo);
    volatileFields.add(binding);
    return binding;
  }

  private static String checkAndGetJsonFieldName(Method m) {
    if (m.getParameterTypes().length != 0) {
      throw new JsonProtocolModelParseException("Must have 0 parameters");
    }
    JsonField fieldAnn = m.getAnnotation(JsonField.class);
    if (fieldAnn != null) {
      String jsonLiteralName = fieldAnn.jsonLiteralName();
      if (!jsonLiteralName.isEmpty()) {
        return jsonLiteralName;
      }
    }
    return m.getName();
  }
}
