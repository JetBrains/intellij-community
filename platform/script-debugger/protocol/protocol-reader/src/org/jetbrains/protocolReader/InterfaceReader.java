package org.jetbrains.protocolReader;

import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.jsonProtocol.JsonField;
import org.jetbrains.jsonProtocol.JsonSubtype;
import org.jetbrains.jsonProtocol.StringIntPair;

import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;

class InterfaceReader {
  private static final PrimitiveValueReader LONG_PARSER = new PrimitiveValueReader("long", "-1");

  private static final PrimitiveValueReader INTEGER_PARSER = new PrimitiveValueReader("int", "-1");
  private static final PrimitiveValueReader NULLABLE_INTEGER_PARSER = new PrimitiveValueReader("int", "-1", true, false);

  private static final PrimitiveValueReader BOOLEAN_PARSER = new PrimitiveValueReader("boolean");
  private static final PrimitiveValueReader FLOAT_PARSER = new PrimitiveValueReader("float");

  private static final PrimitiveValueReader NUMBER_PARSER = new PrimitiveValueReader("double");
  private static final PrimitiveValueReader NULLABLE_NUMBER_PARSER = new PrimitiveValueReader("double", true);

  private static final PrimitiveValueReader STRING_PARSER = new PrimitiveValueReader("String");
  private static final PrimitiveValueReader NULLABLE_STRING_PARSER = new PrimitiveValueReader("String", true);

  private static final PrimitiveValueReader RAW_STRING_PARSER = new PrimitiveValueReader("String", null, false, true);
  private static final PrimitiveValueReader RAW_STRING_OR_MAP_PARSER = new PrimitiveValueReader("Object", null, false, true) {
    @Override
    void writeReadCode(ClassScope methodScope, boolean subtyping, String fieldName, TextOutput out) {
      out.append("readRawStringOrMap(");
      addReaderParameter(subtyping, out);
      out.append(')');
    }
  };

  private static final RawValueReader JSON_PARSER = new RawValueReader(false);
  private static final RawValueReader NULLABLE_JSON_PARSER = new RawValueReader(true);

  private static final MapReader MAP_PARSER = new MapReader(false);
  private static final MapReader NULLABLE_MAP_PARSER = new MapReader(true);

  private static final StringIntPairValueReader STRING_INT_PAIR_PARSER = new StringIntPairValueReader();

  final static ValueReader VOID_PARSER = new ValueReader(true) {
    @Override
    public void appendFinishedValueTypeName(TextOutput out) {
      out.append("void");
    }

    @Override
    void writeReadCode(ClassScope scope, boolean subtyping, String fieldName, TextOutput out) {
      out.append("null");
    }

    @Override
    void writeArrayReadCode(ClassScope scope, boolean subtyping, boolean nullable, String fieldName, TextOutput out) {
      throw new UnsupportedOperationException();
    }
  };

  private final LinkedHashMap<Class<?>, TypeHandler<?>> typeToTypeHandler;

  final List<TypeRef<?>> refs = new ArrayList<>();
  final List<SubtypeCaster> subtypeCasters = new ArrayList<>();

  InterfaceReader(Class<?>[] protocolInterfaces) {
    typeToTypeHandler = new LinkedHashMap<>(protocolInterfaces.length);
    for (Class<?> typeClass : protocolInterfaces) {
      typeToTypeHandler.put(typeClass, null);
    }
  }

  private InterfaceReader(LinkedHashMap<Class<?>, TypeHandler<?>> typeToTypeHandler) {
    this.typeToTypeHandler = typeToTypeHandler;
  }

  public static TypeHandler<?> createHandler(LinkedHashMap<Class<?>, TypeHandler<?>> typeToTypeHandler, Class<?> aClass) {
    InterfaceReader reader = new InterfaceReader(typeToTypeHandler);
    reader.processed.addAll(typeToTypeHandler.keySet());
    reader.go(new Class[]{aClass});
    return typeToTypeHandler.get(aClass);
  }

  LinkedHashMap<Class<?>, TypeHandler<?>> go() {
    return go(typeToTypeHandler.keySet().toArray(new Class[typeToTypeHandler.size()]));
  }

  private LinkedHashMap<Class<?>, TypeHandler<?>> go(Class<?>[] classes) {
    for (Class<?> typeClass : classes) {
      createIfNotExists(typeClass);
    }

    boolean hasUnresolved = true;
    while (hasUnresolved) {
      hasUnresolved = false;
      // refs can be modified - new items can be added
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, n = refs.size(); i < n; i++) {
        TypeRef<?> ref = refs.get(i);
        TypeHandler<?> type = typeToTypeHandler.get(ref.typeClass);
        if (type == null) {
          createIfNotExists(ref.typeClass);
          hasUnresolved = true;
          type = typeToTypeHandler.get(ref.typeClass);
          if (type == null) {
            throw new IllegalStateException();
          }
        }
        ref.set(type);
      }
    }

    for (SubtypeCaster subtypeCaster : subtypeCasters) {
      subtypeCaster.getSubtypeHandler().getSubtypeSupport().setSubtypeCaster(subtypeCaster);
    }

    return typeToTypeHandler;
  }

  private final Set<Class<?>> processed = new THashSet<>();

  private void createIfNotExists(Class<?> typeClass) {
    if (typeClass == Map.class || typeClass == List.class || !typeClass.isInterface()) {
      return;
    }

    if (processed.contains(typeClass)) {
      return;
    }
    processed.add(typeClass);

    typeToTypeHandler.put(typeClass, null);

    for (Class<?> aClass : typeClass.getDeclaredClasses()) {
      createIfNotExists(aClass);
    }

    TypeHandler<?> typeHandler = createTypeHandler(typeClass);
    for (TypeRef<?> ref : refs) {
      if (ref.typeClass == typeClass) {
        assert ref.get() == null;
        ref.set(typeHandler);
        break;
      }
    }
    typeToTypeHandler.put(typeClass, typeHandler);
  }

  private <T> TypeHandler<T> createTypeHandler(Class<T> typeClass) {
    if (!typeClass.isInterface()) {
      throw new JsonProtocolModelParseException("Json model type should be interface: " + typeClass.getName());
    }

    FieldProcessor<T> fields = new FieldProcessor<>(this, typeClass);
    fields.go();

    LinkedHashMap<Method, MethodHandler> methodHandlerMap = fields.getMethodHandlerMap();
    for (Method method : methodHandlerMap.keySet()) {
      Class<?> returnType = method.getReturnType();
      if (returnType != typeClass) {
        createIfNotExists(returnType);
      }
    }

    return new TypeHandler<>(typeClass, getSuperclassRef(typeClass),
                              fields.getVolatileFields(), methodHandlerMap,
                              fields.getFieldLoaders(),
                              fields.lazyRead);
  }

  ValueReader getFieldTypeParser(Type type, boolean declaredNullable, boolean isSubtyping, @Nullable Method method) {
    if (type instanceof Class) {
      Class<?> typeClass = (Class<?>)type;
      if (type == Long.TYPE) {
        nullableIsNotSupported(declaredNullable);
        return LONG_PARSER;
      }
      else if (type == Integer.TYPE) {
        return declaredNullable ? NULLABLE_INTEGER_PARSER : INTEGER_PARSER;
      }
      else if (type == Boolean.TYPE) {
        nullableIsNotSupported(declaredNullable);
        return BOOLEAN_PARSER;
      }
      else if (type == Float.TYPE) {
        nullableIsNotSupported(declaredNullable);
        return FLOAT_PARSER;
      }
      else if (type == Number.class || type == Double.TYPE) {
        return declaredNullable ? NULLABLE_NUMBER_PARSER : NUMBER_PARSER;
      }
      else if (type == Void.TYPE) {
        nullableIsNotSupported(declaredNullable);
        return VOID_PARSER;
      }
      else if (type == String.class) {
        if (declaredNullable) {
          return NULLABLE_STRING_PARSER;
        }
        else {
          if (method != null) {
            JsonField jsonField = method.getAnnotation(JsonField.class);
            if (jsonField != null && jsonField.allowAnyPrimitiveValue()) {
              return RAW_STRING_PARSER;
            }
          }
          return STRING_PARSER;
        }
      }
      else if (type == Object.class) {
        return RAW_STRING_OR_MAP_PARSER;
      }
      else if (type == JsonReaderEx.class) {
        return declaredNullable ? NULLABLE_JSON_PARSER : JSON_PARSER;
      }
      else if (type == Map.class) {
        return declaredNullable ? NULLABLE_MAP_PARSER : MAP_PARSER;
      }
      else if (type == StringIntPair.class) {
        return STRING_INT_PAIR_PARSER;
      }
      else if (typeClass.isArray()) {
        return new ArrayReader(getFieldTypeParser(typeClass.getComponentType(), false, false, null), false,
                               declaredNullable);
      }
      else if (typeClass.isEnum()) {
        //noinspection unchecked
        return EnumReader.create((Class<RetentionPolicy>)typeClass, declaredNullable);
      }
      TypeRef<?> ref = getTypeRef(typeClass);
      if (ref != null) {
        return createJsonParser(ref, declaredNullable, isSubtyping);
      }
      throw new JsonProtocolModelParseException("Method return type " + type + " (simple class) not supported");
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      if (parameterizedType.getRawType() == List.class) {
        Type argumentType = parameterizedType.getActualTypeArguments()[0];
        if (argumentType instanceof WildcardType) {
          WildcardType wildcard = (WildcardType)argumentType;
          if (wildcard.getLowerBounds().length == 0 && wildcard.getUpperBounds().length == 1) {
            argumentType = wildcard.getUpperBounds()[0];
          }
        }
        return new ArrayReader(getFieldTypeParser(argumentType, false, false, method), true, declaredNullable);
      }
      else if (parameterizedType.getRawType() == Map.class) {
        return declaredNullable ? NULLABLE_MAP_PARSER : MAP_PARSER;
      }
      else {
        throw new JsonProtocolModelParseException("Method return type " + type + " (generic) not supported");
      }
    }
    else {
      throw new JsonProtocolModelParseException("Method return type " + type + " not supported");
    }
  }

  private static void nullableIsNotSupported(boolean declaredNullable) {
    if (declaredNullable) {
      throw new JsonProtocolModelParseException("The type cannot be declared nullable");
    }
  }

  private static <T> ObjectValueReader<T> createJsonParser(TypeRef<T> type, boolean isNullable, boolean isSubtyping) {
    return new ObjectValueReader<>(type, isNullable, isSubtyping);
  }

  <T> TypeRef<T> getTypeRef(Class<T> typeClass) {
    TypeRef<T> result = new TypeRef<>(typeClass);
    refs.add(result);
    return result;
  }

  private TypeRef<?> getSuperclassRef(Class<?> typeClass) {
    TypeRef<?> result = null;
    for (Type interfaceGeneric : typeClass.getGenericInterfaces()) {
      if (!(interfaceGeneric instanceof ParameterizedType)) {
        continue;
      }
      ParameterizedType parameterizedType = (ParameterizedType)interfaceGeneric;
      if (parameterizedType.getRawType() != JsonSubtype.class) {
        continue;
      }
      Type param = parameterizedType.getActualTypeArguments()[0];
      if (!(param instanceof Class)) {
        throw new JsonProtocolModelParseException("Unexpected type of superclass " + param);
      }
      Class<?> paramClass = (Class<?>)param;
      if (result != null) {
        throw new JsonProtocolModelParseException("Already has superclass " +
                                                  result.getTypeClass().getName());
      }
      result = getTypeRef(paramClass);
      if (result == null) {
        throw new JsonProtocolModelParseException("Unknown base class " + paramClass.getName());
      }
    }
    return result;
  }
}