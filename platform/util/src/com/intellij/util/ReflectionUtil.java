/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReflectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ReflectionUtil");

  private ReflectionUtil() {
  }

  @Nullable
  public static Type resolveVariable(@NotNull TypeVariable variable, @NotNull Class classType) {
    return resolveVariable(variable, classType, true);
  }

  @Nullable
  public static Type resolveVariable(@NotNull TypeVariable variable, @NotNull Class classType, boolean resolveInInterfacesOnly) {
    final Class aClass = getRawType(classType);
    int index = ArrayUtilRt.find(ReflectionCache.getTypeParameters(aClass), variable);
    if (index >= 0) {
      return variable;
    }

    final Class[] classes = ReflectionCache.getInterfaces(aClass);
    final Type[] genericInterfaces = ReflectionCache.getGenericInterfaces(aClass);
    for (int i = 0; i <= classes.length; i++) {
      Class anInterface;
      if (i < classes.length) {
        anInterface = classes[i];
      }
      else {
        anInterface = ReflectionCache.getSuperClass(aClass);
        if (resolveInInterfacesOnly || anInterface == null) {
          continue;
        }
      }
      final Type resolved = resolveVariable(variable, anInterface);
      if (resolved instanceof Class || resolved instanceof ParameterizedType) {
        return resolved;
      }
      if (resolved instanceof TypeVariable) {
        final TypeVariable typeVariable = (TypeVariable)resolved;
        index = ArrayUtilRt.find(ReflectionCache.getTypeParameters(anInterface), typeVariable);
        if (index < 0) {
          LOG.error("Cannot resolve type variable:\n" + "typeVariable = " + typeVariable + "\n" + "genericDeclaration = " +
                    declarationToString(typeVariable.getGenericDeclaration()) + "\n" + "searching in " + declarationToString(anInterface));
        }
        final Type type = i < genericInterfaces.length ? genericInterfaces[i] : aClass.getGenericSuperclass();
        if (type instanceof Class) {
          return Object.class;
        }
        if (type instanceof ParameterizedType) {
          return getActualTypeArguments((ParameterizedType)type)[index];
        }
        throw new AssertionError("Invalid type: " + type);
      }
    }
    return null;
  }

  @NotNull
  public static String declarationToString(@NotNull GenericDeclaration anInterface) {
    return anInterface.toString()
           + Arrays.asList(anInterface.getTypeParameters())
           + " loaded by " + ((Class)anInterface).getClassLoader();
  }

  public static Class<?> getRawType(@NotNull Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getRawType(((ParameterizedType)type).getRawType());
    }
    if (type instanceof GenericArrayType) {
      //todo[peter] don't create new instance each time
      return Array.newInstance(getRawType(((GenericArrayType)type).getGenericComponentType()), 0).getClass();
    }
    assert false : type;
    return null;
  }

  @NotNull
  public static Type[] getActualTypeArguments(@NotNull ParameterizedType parameterizedType) {
    return ReflectionCache.getActualTypeArguments(parameterizedType);
  }

  @Nullable
  public static Class<?> substituteGenericType(@NotNull Type genericType, @NotNull Type classType) {
    if (genericType instanceof TypeVariable) {
      final Class<?> aClass = getRawType(classType);
      final Type type = resolveVariable((TypeVariable)genericType, aClass);
      if (type instanceof Class) {
        return (Class)type;
      }
      if (type instanceof ParameterizedType) {
        return (Class<?>)((ParameterizedType)type).getRawType();
      }
      if (type instanceof TypeVariable && classType instanceof ParameterizedType) {
        final int index = ArrayUtilRt.find(ReflectionCache.getTypeParameters(aClass), type);
        if (index >= 0) {
          return getRawType(getActualTypeArguments((ParameterizedType)classType)[index]);
        }
      }
    } else {
      return getRawType(genericType);
    }
    return null;
  }

  @NotNull
  public static List<Field> collectFields(@NotNull Class clazz) {
    List<Field> result = new ArrayList<Field>();
    collectFields(clazz, result);
    return result;
  }

  @NotNull
  public static Field findField(@NotNull Class clazz, @Nullable Class type, @NotNull String name) throws NoSuchFieldException {
    List<Field> fields = collectFields(clazz);
    for (Field each : fields) {
      if (name.equals(each.getName()) && (type == null || each.getType().equals(type))) return each;
    }

    throw new NoSuchFieldException("Class: " + clazz + " name: " + name + " type: " + type);
  }

  @NotNull
  public static Field findAssignableField(@NotNull Class clazz, @NotNull Class type, @NotNull String name) throws NoSuchFieldException {
    List<Field> fields = collectFields(clazz);
    for (Field each : fields) {
      if (name.equals(each.getName()) && type.isAssignableFrom(each.getType())) return each;
    }

    throw new NoSuchFieldException("Class: " + clazz + " name: " + name + " type: " + type);
  }

  private static void collectFields(@NotNull Class clazz, @NotNull List<Field> result) {
    final Field[] fields = clazz.getDeclaredFields();
    result.addAll(Arrays.asList(fields));
    final Class superClass = clazz.getSuperclass();
    if (superClass != null) {
      collectFields(superClass, result);
    }
    final Class[] interfaces = clazz.getInterfaces();
    for (Class each : interfaces) {
      collectFields(each, result);
    }
  }

  public static void resetField(@NotNull Class clazz, @NotNull Class type, @NotNull String name)  {
    try {
      resetField(null, findField(clazz, type, name));
    }
    catch (NoSuchFieldException e) {
      LOG.info(e);
    }
  }
  public static void resetField(@NotNull Object object, @NotNull Class type, @NotNull String name)  {
    try {
      resetField(object, findField(object.getClass(), type, name));
    }
    catch (NoSuchFieldException e) {
      LOG.info(e);
    }
  }

  public static void resetField(@NotNull Object object, @NotNull String name) {
    try {
      resetField(object, findField(object.getClass(), null, name));
    }
    catch (NoSuchFieldException e) {
      LOG.info(e);
    }
  }

  public static void resetField(@Nullable final Object object, @NotNull Field field) {
    field.setAccessible(true);
    Class<?> type = field.getType();
    try {
      if (type.isPrimitive()) {
        if (boolean.class.equals(type)) {
          field.set(object, Boolean.FALSE);
        }
        else if (int.class.equals(type)) {
          field.set(object, new Integer(0));
        }
        else if (double.class.equals(type)) {
          field.set(object, new Double(0));
        }
        else if (float.class.equals(type)) {
          field.set(object, new Float(0));
        }
      }
      else {
        field.set(object, null);
      }
    }
    catch (IllegalAccessException e) {
      LOG.info(e);
    }
  }

  @Nullable
  public static Method findMethod(@NotNull Method[] methods, @NonNls @NotNull String name, @NotNull Class... parameters) {
    for (final Method method : methods) {
      if (name.equals(method.getName()) && Arrays.equals(parameters, method.getParameterTypes())) return method;
    }
    return null;
  }

  @Nullable
  public static Method getMethod(@NotNull Class aClass, @NonNls @NotNull String name, @NotNull Class... parameters) {
    return findMethod(ReflectionCache.getMethods(aClass), name, parameters);
  }

  @Nullable
  public static Method getDeclaredMethod(@NotNull Class aClass, @NonNls @NotNull String name, @NotNull Class... parameters) {
    return findMethod(aClass.getDeclaredMethods(), name, parameters);
  }

  public static <T> T getField(@NotNull Class objectClass, Object object, @NotNull Class<T> type, @NotNull @NonNls String name) {
    try {
      final Field field = findAssignableField(objectClass, type, name);
      field.setAccessible(true);
      return (T)field.get(object);
    }
    catch (NoSuchFieldException e) {
      LOG.debug(e);
      return null;
    }
    catch (IllegalAccessException e) {
      LOG.debug(e);
      return null;
    }
  }

  public static Type resolveVariableInHierarchy(@NotNull TypeVariable variable, @NotNull Class aClass) {
    Type type;
    Class current = aClass;
    while ((type = resolveVariable(variable, current, false)) == null) {
      current = ReflectionCache.getSuperClass(current);
      if (current == null) {
        return null;
      }
    }
    if (type instanceof TypeVariable) {
      return resolveVariableInHierarchy((TypeVariable)type, aClass);
    }
    return type;
  }

  @NotNull
  public static <T> Constructor<T> getDefaultConstructor(@NotNull Class<T> aClass) {
    try {
      final Constructor<T> constructor = aClass.getConstructor();
      constructor.setAccessible(true);
      return constructor;
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException("No default constructor in " + aClass, e);
    }
  }

  @NotNull
  public static <T> T createInstance(@NotNull Constructor<T> constructor, @NotNull Object... args) {
    try {
      return constructor.newInstance(args);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void resetThreadLocals() {
    try {
      Field field = Thread.class.getDeclaredField("threadLocals");
      field.setAccessible(true);
      field.set(Thread.currentThread(), null);
    }
    catch (Throwable e) {
      LOG.info(e);
    }
  }


  private static class MySecurityManager extends SecurityManager {
    private static final MySecurityManager INSTANCE = new MySecurityManager();
    public Class[] getStack() {
      return getClassContext();
    }
  }

  /**
   * Returns the class this method was called 'framesToSkip' frames up the caller hierarchy.
   *
   * NOTE:
   * <b>Extremely expensive!
   * Please consider not using it.
   * These aren't the droids you're looking for!</b>
   */
  @Nullable
  public static Class findCallerClass(int framesToSkip) {
    try {
      Class[] stack = MySecurityManager.INSTANCE.getStack();
      int indexFromTop = 1 + framesToSkip;
      return stack.length > indexFromTop ? stack[indexFromTop] : null;
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }
  }
}
