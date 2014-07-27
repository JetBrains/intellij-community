package org.jetbrains.protocolReader;

import org.jetbrains.io.JsonReaderEx;
import gnu.trove.THashSet;
import org.jetbrains.jsonProtocol.JsonParseMethod;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

class ReaderRoot<R> {
  private final Class<R> rootClass;

  private final LinkedHashMap<Class<?>, TypeHandler<?>> typeToTypeHandler;
  private final Set<Class<?>> visitedInterfaces = new THashSet<>(1);
  final LinkedHashMap<Method, ReadDelegate> methodMap = new LinkedHashMap<>();

  ReaderRoot(Class<R> rootClass, LinkedHashMap<Class<?>, TypeHandler<?>> typeToTypeHandler) {
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
      public int compare(Method o1, Method o2) {
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
      TypeHandler<?> typeHandler = typeToTypeHandler.get(returnType);
      if (typeHandler == null) {
        typeHandler = InterfaceReader.createHandler(typeToTypeHandler, m.getReturnType());
        if (typeHandler == null) {
          throw new JsonProtocolModelParseException("Unknown return type in " + m);
        }
      }

      Type[] arguments = m.getGenericParameterTypes();
      if (arguments.length != 1) {
        throw new JsonProtocolModelParseException("Exactly one argument is expected in " + m);
      }
      Type argument = arguments[0];
      if (argument == JsonReaderEx.class || argument == Object.class) {
        methodMap.put(m, new ReadDelegate(typeHandler, isList));
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

  public void writeStaticMethodJava(ClassScope scope) {
    TextOutput out = scope.getOutput();
    for (Map.Entry<Method, ReadDelegate> en : methodMap.entrySet()) {
      out.newLine();
      en.getValue().write(scope, en.getKey(), out);
      out.newLine();
    }
  }
}