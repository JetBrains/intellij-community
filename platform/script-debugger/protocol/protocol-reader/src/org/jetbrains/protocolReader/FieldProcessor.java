package org.jetbrains.protocolReader;

import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jsonProtocol.JsonField;
import org.jetbrains.jsonProtocol.JsonOptionalField;
import org.jetbrains.jsonProtocol.JsonSubtypeCasting;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

final class FieldProcessor<T> {
  final List<FieldLoader> fieldLoaders = new ArrayList<>();
  final LinkedHashMap<Method, MethodHandler> methodHandlerMap = new LinkedHashMap<>();
  final List<VolatileFieldBinding> volatileFields = new ArrayList<>();
  boolean lazyRead;
  private final InterfaceReader reader;

  FieldProcessor(@NotNull InterfaceReader reader, @NotNull Class<T> typeClass) {
    this.reader = reader;

    Method[] methods = typeClass.getMethods();
    // todo sort by source location
    Arrays.sort(methods, (o1, o2) -> o1.getName().compareTo(o2.getName()));

    Set<String> skippedNames = new THashSet<>();
    for (Method method : methods) {
      JsonField annotation = method.getAnnotation(JsonField.class);
      if (annotation != null && !annotation.primitiveValue().isEmpty()) {
        skippedNames.add(annotation.primitiveValue());
        skippedNames.add(annotation.primitiveValue() + "Type");
      }
    }

    Package classPackage = typeClass.getPackage();
    for (Method method : methods) {
      Class<?> methodClass = method.getDeclaringClass();
      // use method from super if super located in the same package
      if (methodClass != typeClass) {
        Package methodPackage = methodClass.getPackage();
        // may be it will be useful later
        // && !methodPackage.getName().equals("org.jetbrains.debugger.adapters")
        if (methodPackage != classPackage) {
          continue;
        }
      }

      if (method.getParameterCount() != 0) {
        throw new JsonProtocolModelParseException("No parameters expected in " + method);
      }

      try {
        MethodHandler methodHandler;
        JsonSubtypeCasting jsonSubtypeCaseAnnotation = method.getAnnotation(JsonSubtypeCasting.class);
        if (jsonSubtypeCaseAnnotation == null) {
          methodHandler = createMethodHandler(method, skippedNames.contains(method.getName()));
        }
        else {
          methodHandler = processManualSubtypeMethod(method, jsonSubtypeCaseAnnotation);
          lazyRead = true;
        }
        methodHandlerMap.put(method, methodHandler);
      }
      catch (Exception e) {
        throw new JsonProtocolModelParseException("Problem with method " + method, e);
      }
    }
  }

  @NotNull
  private MethodHandler createMethodHandler(@NotNull Method method, boolean skipRead) {
    String jsonName = method.getName();
    JsonField fieldAnnotation = method.getAnnotation(JsonField.class);
    if (fieldAnnotation != null && !fieldAnnotation.name().isEmpty()) {
      jsonName = fieldAnnotation.name();
    }

    Type genericReturnType = method.getGenericReturnType();
    boolean addNotNullAnnotation;
    boolean isPrimitive = genericReturnType instanceof Class ? ((Class)genericReturnType).isPrimitive() : !(genericReturnType instanceof ParameterizedType);
    if (isPrimitive) {
      addNotNullAnnotation = false;
    }
    else if (fieldAnnotation != null) {
      addNotNullAnnotation = !fieldAnnotation.optional() && !fieldAnnotation.allowAnyPrimitiveValue() && !fieldAnnotation.allowAnyPrimitiveValueAndMap();
    }
    else {
      addNotNullAnnotation = method.getAnnotation(JsonOptionalField.class) == null;
    }

    ValueReader fieldTypeParser = reader.getFieldTypeParser(genericReturnType, false, method);
    if (fieldTypeParser != InterfaceReader.VOID_PARSER) {
      fieldLoaders.add(new FieldLoader(method.getName(), jsonName, fieldTypeParser, skipRead));
    }

    final String effectiveFieldName = fieldTypeParser == InterfaceReader.VOID_PARSER ? null : method.getName();
    return new MethodHandler() {
      @Override
      void writeMethodImplementationJava(@NotNull ClassScope scope, @NotNull Method method, @NotNull TextOutput out) {
        if (addNotNullAnnotation) {
          out.append("@NotNull").newLine();
        }
        writeMethodDeclarationJava(out, method);
        out.openBlock();
        if (effectiveFieldName != null) {
          out.append("return ").append(TypeWriter.FIELD_PREFIX).append(effectiveFieldName).semi();
        }
        out.closeBlock();
      }
    };
  }

  private MethodHandler processManualSubtypeMethod(final Method m, JsonSubtypeCasting jsonSubtypeCaseAnn) {
    ValueReader fieldTypeParser = reader.getFieldTypeParser(m.getGenericReturnType(), !jsonSubtypeCaseAnn.reinterpret(), null);
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

  @NotNull
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
}
