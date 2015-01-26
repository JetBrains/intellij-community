package org.jetbrains.protocolReader;

import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.jsonProtocol.JsonParseMethod;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

class ReaderRoot<R> {
  private final Class<R> rootClass;

  private final LinkedHashMap<Class<?>, TypeWriter<?>> typeToTypeHandler;
  private final Set<Class<?>> visitedInterfaces = new THashSet<>(1);
  final LinkedHashMap<Method, ReadDelegate> methodMap = new LinkedHashMap<>();

  ReaderRoot(Class<R> rootClass, LinkedHashMap<Class<?>, TypeWriter<?>> typeToTypeHandler) {
    this.rootClass = rootClass;
    this.typeToTypeHandler = typeToTypeHandler;
    readInterfaceRecursive(rootClass);
  }

  private void readInterfaceRecursive(Class<?> clazz) throws JsonProtocolModelParseException {
    if (visitedInterfaces.contains(clazz)) {
      return;
    }
    visitedInterfaces.add(clazz);

    // todo sort by source location
    Method[] methods = clazz.getMethods();
    Arrays.sort(methods, new Comparator<Method>() {
      @Override
      public int compare(@NotNull Method o1, @NotNull Method o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    for (Method m : methods) {
      JsonParseMethod jsonParseMethod = m.getAnnotation(JsonParseMethod.class);
      if (jsonParseMethod == null) {
        continue;
      }

      Class<?>[] exceptionTypes = m.getExceptionTypes();
      if (exceptionTypes.length > 1) {
        throw new JsonProtocolModelParseException("Too many exception declared in " + m);
      }

      Type returnType = m.getGenericReturnType();
      boolean isList = false;
      if (returnType instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType)returnType;
        if (parameterizedType.getRawType() == List.class) {
          isList = true;
          returnType = parameterizedType.getActualTypeArguments()[0];
        }
      }

      //noinspection SuspiciousMethodCalls
      TypeWriter<?> typeWriter = typeToTypeHandler.get(returnType);
      if (typeWriter == null) {
        typeWriter = InterfaceReader.createHandler(typeToTypeHandler, m.getReturnType());
        if (typeWriter == null) {
          throw new JsonProtocolModelParseException("Unknown return type in " + m);
        }
      }

      Type[] arguments = m.getGenericParameterTypes();
      if (arguments.length > 2) {
        throw new JsonProtocolModelParseException("Exactly one argument is expected in " + m);
      }
      Type argument = arguments[0];
      if (argument == JsonReaderEx.class || argument == Object.class) {
        methodMap.put(m, new ReadDelegate(typeWriter, isList, arguments.length != 1));
      }
      else {
        throw new JsonProtocolModelParseException("Unrecognized argument type in " + m);
      }
    }

    for (Type baseType : clazz.getGenericInterfaces()) {
      if (!(baseType instanceof Class)) {
        throw new JsonProtocolModelParseException("Base interface must be class in " + clazz);
      }
      Class<?> baseClass = (Class<?>) baseType;
      readInterfaceRecursive(baseClass);
    }
  }

  public Class<R> getType() {
    return rootClass;
  }

  public void writeStaticMethodJava(@NotNull ClassScope scope) {
    TextOutput out = scope.getOutput();
    for (Map.Entry<Method, ReadDelegate> entry : methodMap.entrySet()) {
      out.newLine();
      entry.getValue().write(scope, entry.getKey(), out);
      out.newLine();
    }
  }
}