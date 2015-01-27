package org.jetbrains.protocolReader;

import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
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

  private static final PrimitiveValueReader BOOLEAN_PARSER = new PrimitiveValueReader("boolean");
  private static final PrimitiveValueReader FLOAT_PARSER = new PrimitiveValueReader("float");

  private static final PrimitiveValueReader NUMBER_PARSER = new PrimitiveValueReader("double");

  private static final PrimitiveValueReader STRING_PARSER = new PrimitiveValueReader("String");

  private static final PrimitiveValueReader RAW_STRING_PARSER = new PrimitiveValueReader("String", null, true);
  private static final PrimitiveValueReader RAW_STRING_OR_MAP_PARSER = new PrimitiveValueReader("Object", null, true) {
    @Override
    void writeReadCode(ClassScope methodScope, boolean subtyping, @NotNull TextOutput out) {
      out.append("readRawStringOrMap(");
      addReaderParameter(subtyping, out);
      out.append(')');
    }
  };

  private static final RawValueReader JSON_PARSER = new RawValueReader(false);

  private static final MapReader MAP_PARSER = new MapReader(null);

  private static final StringIntPairValueReader STRING_INT_PAIR_PARSER = new StringIntPairValueReader();

  final static ValueReader VOID_PARSER = new ValueReader() {
    @Override
    public void appendFinishedValueTypeName(@NotNull TextOutput out) {
      out.append("void");
    }

    @Override
    void writeReadCode(ClassScope scope, boolean subtyping, @NotNull TextOutput out) {
      out.append("null");
    }
  };

  private final LinkedHashMap<Class<?>, TypeWriter<?>> typeToTypeHandler;

  final List<TypeRef<?>> refs = new ArrayList<>();
  final List<SubtypeCaster> subtypeCasters = new ArrayList<>();

  InterfaceReader(Class<?>[] protocolInterfaces) {
    typeToTypeHandler = new LinkedHashMap<>(protocolInterfaces.length);
    for (Class<?> typeClass : protocolInterfaces) {
      typeToTypeHandler.put(typeClass, null);
    }
  }

  private InterfaceReader(LinkedHashMap<Class<?>, TypeWriter<?>> typeToTypeHandler) {
    this.typeToTypeHandler = typeToTypeHandler;
  }

  public static TypeWriter<?> createHandler(LinkedHashMap<Class<?>, TypeWriter<?>> typeToTypeHandler, Class<?> aClass) {
    InterfaceReader reader = new InterfaceReader(typeToTypeHandler);
    reader.processed.addAll(typeToTypeHandler.keySet());
    reader.go(new Class[]{aClass});
    return typeToTypeHandler.get(aClass);
  }

  LinkedHashMap<Class<?>, TypeWriter<?>> go() {
    return go(typeToTypeHandler.keySet().toArray(new Class[typeToTypeHandler.size()]));
  }

  private LinkedHashMap<Class<?>, TypeWriter<?>> go(Class<?>[] classes) {
    for (Class<?> typeClass : classes) {
      createIfNotExists(typeClass);
    }

    boolean hasUnresolved = true;
    while (hasUnresolved) {
      hasUnresolved = false;
      // refs can be modified - new items can be added
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, n = refs.size(); i < n; i++) {
        TypeRef ref = refs.get(i);
        ref.type = typeToTypeHandler.get(ref.typeClass);
        if (ref.type == null) {
          createIfNotExists(ref.typeClass);
          hasUnresolved = true;
          ref.type = typeToTypeHandler.get(ref.typeClass);
          if (ref.type == null) {
            throw new IllegalStateException();
          }
        }
      }
    }

    for (SubtypeCaster subtypeCaster : subtypeCasters) {
      ExistingSubtypeAspect subtypeSupport = subtypeCaster.getSubtypeHandler().subtypeAspect;
      if (subtypeSupport != null) {
        subtypeSupport.setSubtypeCaster(subtypeCaster);
      }
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

    if (!typeClass.isInterface()) {
      throw new JsonProtocolModelParseException("Json model type should be interface: " + typeClass.getName());
    }

    FieldProcessor<?> fields = new FieldProcessor<>(this, typeClass);
    for (Method method : fields.methodHandlerMap.keySet()) {
      Class<?> returnType = method.getReturnType();
      if (returnType != typeClass) {
        createIfNotExists(returnType);
      }
    }

    TypeWriter<?> typeWriter = new TypeWriter<>(typeClass, getSuperclassRef(typeClass),
                                                   fields.volatileFields, fields.methodHandlerMap,
                                                   fields.fieldLoaders,
                                                   fields.lazyRead);
    for (TypeRef ref : refs) {
      if (ref.typeClass == typeClass) {
        assert ref.type == null;
        ref.type = typeWriter;
        break;
      }
    }
    typeToTypeHandler.put(typeClass, typeWriter);
  }

  ValueReader getFieldTypeParser(Type type, boolean isSubtyping, @Nullable Method method) {
    if (type instanceof Class) {
      Class<?> typeClass = (Class<?>)type;
      if (type == Long.TYPE) {
        return LONG_PARSER;
      }
      else if (type == Integer.TYPE) {
        return INTEGER_PARSER;
      }
      else if (type == Boolean.TYPE) {
        return BOOLEAN_PARSER;
      }
      else if (type == Float.TYPE) {
        return FLOAT_PARSER;
      }
      else if (type == Number.class || type == Double.TYPE) {
        return NUMBER_PARSER;
      }
      else if (type == Void.TYPE) {
        return VOID_PARSER;
      }
      else if (type == String.class) {
        if (method != null) {
          JsonField jsonField = method.getAnnotation(JsonField.class);
          if (jsonField != null && jsonField.allowAnyPrimitiveValue()) {
            return RAW_STRING_PARSER;
          }
        }
        return STRING_PARSER;
      }
      else if (type == Object.class) {
        return RAW_STRING_OR_MAP_PARSER;
      }
      else if (type == JsonReaderEx.class) {
        return JSON_PARSER;
      }
      else if (type == Map.class) {
        return MAP_PARSER;
      }
      else if (type == StringIntPair.class) {
        return STRING_INT_PAIR_PARSER;
      }
      else if (typeClass.isArray()) {
        return new ArrayReader(getFieldTypeParser(typeClass.getComponentType(), false, null), false);
      }
      else if (typeClass.isEnum()) {
        //noinspection unchecked
        return EnumReader.create((Class<RetentionPolicy>)typeClass);
      }
      TypeRef<?> ref = getTypeRef(typeClass);
      if (ref != null) {
        JsonField jsonField = method == null ? null : method.getAnnotation(JsonField.class);
        return new ObjectValueReader<>(ref, isSubtyping, jsonField == null ? null : jsonField.primitiveValue());
      }
      throw new UnsupportedOperationException("Method return type " + type + " (simple class) not supported");
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      boolean isList = parameterizedType.getRawType() == List.class;
      if (isList || parameterizedType.getRawType() == Map.class) {
        Type argumentType = parameterizedType.getActualTypeArguments()[isList ? 0 : 1];
        if (argumentType instanceof WildcardType) {
          WildcardType wildcard = (WildcardType)argumentType;
          if (wildcard.getLowerBounds().length == 0 && wildcard.getUpperBounds().length == 1) {
            argumentType = wildcard.getUpperBounds()[0];
          }
        }
        ValueReader componentParser = getFieldTypeParser(argumentType, false, method);
        return isList ? new ArrayReader(componentParser, true) : new MapReader(componentParser);
      }
      else {
        throw new UnsupportedOperationException("Method return type " + type + " (generic) not supported");
      }
    }
    else {
      throw new UnsupportedOperationException("Method return type " + type + " not supported");
    }
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
        throw new JsonProtocolModelParseException("Already has superclass " + result.typeClass.getName());
      }
      result = getTypeRef(paramClass);
      if (result == null) {
        throw new JsonProtocolModelParseException("Unknown base class " + paramClass.getName());
      }
    }
    return result;
  }
}