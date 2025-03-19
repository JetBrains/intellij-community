// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

public final class ReflectionUtil {
  private static final Logger LOG = Logger.getInstance(ReflectionUtil.class);

  private ReflectionUtil() { }

  public static @NotNull List<Field> collectFields(@NotNull Class<?> clazz) {
    List<Field> result = new ArrayList<>();
    for (Class<?> c : JBIterableClassTraverser.classTraverser(clazz)) {
      Collections.addAll(result, c.getDeclaredFields());
    }
    return result;
  }

  public static @NotNull Field findField(@NotNull Class<?> clazz, final @Nullable Class<?> type, final @NotNull @NonNls String name) throws NoSuchFieldException {
    Field result = findFieldInHierarchy(clazz, field -> name.equals(field.getName()) && (type == null || field.getType().equals(type)));
    if (result != null) return result;

    throw new NoSuchFieldException("Class: " + clazz + " name: " + name + " type: " + type);
  }

  public static @NotNull Field findAssignableField(@NotNull Class<?> clazz, final @Nullable("null means any type") Class<?> fieldType, @NotNull @NonNls String fieldName) throws NoSuchFieldException {
    Field result = findFieldInHierarchy(clazz, field -> fieldName.equals(field.getName()) && (fieldType == null || fieldType.isAssignableFrom(field.getType())));
    if (result != null) {
      return result;
    }
    throw new NoSuchFieldException("Class: " + clazz + " fieldName: " + fieldName + " fieldType: " + fieldType);
  }

  public static @Nullable Field findFieldInHierarchy(@NotNull Class<?> rootClass,
                                                     @NotNull Predicate<? super Field> checker) {
    for (Class<?> aClass = rootClass; aClass != null; aClass = aClass.getSuperclass()) {
      for (Field field : aClass.getDeclaredFields()) {
        if (checker.test(field)) {
          field.setAccessible(true);
          return field;
        }
      }
    }

    // ok, let's check interfaces
    return processInterfaces(rootClass.getInterfaces(), new HashSet<>(), checker);
  }

  private static @Nullable Field processInterfaces(Class<?> @NotNull [] interfaces,
                                                   @NotNull Set<? super Class<?>> visited,
                                                   @NotNull Predicate<? super Field> checker) {
    for (Class<?> anInterface : interfaces) {
      if (!visited.add(anInterface)) {
        continue;
      }

      for (Field field : anInterface.getDeclaredFields()) {
        if (checker.test(field)) {
          field.setAccessible(true);
          return field;
        }
      }

      Field field = processInterfaces(anInterface.getInterfaces(), visited, checker);
      if (field != null) {
        return field;
      }
    }
    return null;
  }

  public static void resetField(@NotNull Class<?> clazz, @Nullable("null means of any type") Class<?> type, @NotNull @NonNls String name)  {
    try {
      resetField(null, findField(clazz, type, name));
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  public static void resetField(@NotNull Object object, @NotNull @NonNls String name) {
    try {
      resetField(object, findField(object.getClass(), null, name));
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  public static void resetField(final @Nullable Object object, @NotNull Field field) {
    field.setAccessible(true);
    Class<?> type = field.getType();
    try {
      if (type.isPrimitive()) {
        if (boolean.class.equals(type)) {
          field.set(object, Boolean.FALSE);
        }
        else if (int.class.equals(type)) {
          field.set(object, 0);
        }
        else if (double.class.equals(type)) {
          field.set(object, (double)0);
        }
        else if (float.class.equals(type)) {
          field.set(object, (float)0);
        }
      }
      else {
        field.set(object, null);
      }
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static @Nullable Method findMethod(@NotNull Collection<Method> methods, @NonNls @NotNull String name, Class<?> @NotNull ... parameters) {
    for (final Method method : methods) {
      if (parameters.length == method.getParameterCount() && name.equals(method.getName()) && Arrays.equals(parameters, method.getParameterTypes())) {
        return makeAccessible(method);
      }
    }
    return null;
  }

  private static Method makeAccessible(Method method) {
    method.setAccessible(true);
    return method;
  }

  public static @Nullable Method getMethod(@NotNull Class<?> aClass, @NonNls @NotNull String name, Class<?> @NotNull ... parameters) {
    try {
      return makeAccessible(aClass.getMethod(name, parameters));
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  /**
   * @deprecated Use {@link java.lang.invoke.MethodHandles} instead and try to avoid using of a closed API.
   * @see java.lang.invoke.MethodHandles
   * @see java.lang.invoke.MethodHandles.Lookup#findVirtual
   * @see java.lang.invoke.MethodHandles.Lookup#findStatic
   * @see com.jetbrains.internal.JBRApi
   */
  @Deprecated
  public static @Nullable Method getDeclaredMethod(@NotNull Class<?> aClass, @NonNls @NotNull String name, Class<?> @NotNull ... parameters) {
    try {
      return makeAccessible(aClass.getDeclaredMethod(name, parameters));
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  /**
   * @deprecated Use {@link java.lang.invoke.MethodHandles} instead and try to avoid using of a closed API.
   * @see java.lang.invoke.MethodHandles
   * @see java.lang.invoke.MethodHandles.Lookup#findGetter 
   * @see java.lang.invoke.MethodHandles.Lookup#findSetter  
   * @see java.lang.invoke.MethodHandles.Lookup#findStaticGetter   
   * @see java.lang.invoke.MethodHandles.Lookup#findStaticSetter
   * @see com.jetbrains.internal.JBRApi
   */
  @Deprecated
  public static @Nullable Field getDeclaredField(@NotNull Class<?> aClass, @NonNls @NotNull String name) {
    return findFieldInHierarchy(aClass, field -> name.equals(field.getName()));
  }

  public static @NotNull List<Method> getClassPublicMethods(@NotNull Class<?> aClass) {
    return filterRealMethods(aClass.getMethods());
  }

  public static @NotNull List<Method> getClassDeclaredMethods(@NotNull Class<?> aClass) {
    return filterRealMethods(aClass.getDeclaredMethods());
  }

  private static @NotNull List<Method> filterRealMethods(Method @NotNull [] methods) {
    List<Method> result = new ArrayList<>();
    for (Method method : methods) {
      if (!method.isSynthetic()) {
        result.add(method);
      }
    }
    return result;
  }

  public static @Nullable Class<?> getMethodDeclaringClass(@NotNull Class<?> instanceClass, @NonNls @NotNull String methodName, Class<?> @NotNull ... parameters) {
    try {
      return instanceClass.getMethod(methodName, parameters).getDeclaringClass();
    }
    catch (NoSuchMethodException ignore) {
    }

    while (instanceClass != null) {
      try {
        return instanceClass.getDeclaredMethod(methodName, parameters).getDeclaringClass();
      }
      catch (NoSuchMethodException ignored) {
      }
      instanceClass = instanceClass.getSuperclass();
    }
    return null;
  }

  public static <T> T getField(@NotNull Class<?> objectClass, @Nullable Object object, @Nullable("null means any type") Class<T> fieldType, @NotNull @NonNls String fieldName) {
    try {
      Field field = findAssignableField(objectClass, fieldType, fieldName);
      return getFieldValue(field, object);
    }
    catch (NoSuchFieldException e) {
      LOG.debug(e);
      return null;
    }
  }

  public static <T> T getStaticFieldValue(@NotNull Class<?> objectClass, @Nullable("null means any type") Class<T> fieldType, @NotNull @NonNls String fieldName) {
    try {
      final Field field = findAssignableField(objectClass, fieldType, fieldName);
      if (isInstanceField(field)) {
        throw new IllegalArgumentException("Field " + objectClass + "." + fieldName + " is not static");
      }
      return getFieldValue(field, null);
    }
    catch (NoSuchFieldException e) {
      LOG.debug(e);
      return null;
    }
  }

  public static <T> @Nullable T getFieldValue(@NotNull Field field, @Nullable Object object) {
    try {
      //noinspection unchecked
      return (T)field.get(object);
    }
    catch (IllegalAccessException e) {
      LOG.debug(e);
      return null;
    }
  }

  public static boolean isInstanceField(@NotNull Field field) {
    return !Modifier.isStatic(field.getModifiers());
  }

  // returns true if value was set
  public static <T> boolean setField(@NotNull Class<?> objectClass,
                                     Object object,
                                     @Nullable("null means any type") Class<T> fieldType,
                                     @NotNull @NonNls String fieldName,
                                     T value) {
    try {
      final Field field = findAssignableField(objectClass, fieldType, fieldName);
      field.set(object, value);
      return true;
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      LOG.debug(e);
      // this 'return' was moved into 'catch' block because otherwise reference to common super-class of these exceptions (ReflectiveOperationException)
      // which doesn't exist in JDK 1.6 will be added to class-file during instrumentation
      return false;
    }
  }

  public static @NotNull <T> Constructor<T> getDefaultConstructor(@NotNull Class<T> aClass) {
    try {
      final Constructor<T> constructor = aClass.getConstructor();
      constructor.setAccessible(true);
      return constructor;
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException("No default constructor in " + aClass, e);
    }
  }

  /**
   * Handles private classes.
   */
  public static @NotNull <T> T newInstance(@NotNull Class<T> aClass) {
    return newInstance(aClass, true);
  }

  public static @NotNull <T> T newInstance(@NotNull Class<T> aClass, boolean isKotlinDataClassesSupported) {
    try {
      Constructor<T> constructor = aClass.getDeclaredConstructor();
      try {
        constructor.setAccessible(true);
      }
      catch (SecurityException ignored) {
      }
      return constructor.newInstance();
    }
    catch (Exception e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof InvocationTargetException) {
        Throwable targetException = ((InvocationTargetException)e).getTargetException();
        // handle ExtensionNotApplicableException also (extends ControlFlowException)
        if (targetException instanceof ControlFlowException && targetException instanceof RuntimeException) {
          throw (RuntimeException)targetException;
        }
      }

      if (isKotlinDataClassesSupported) {
        T t = createAsDataClass(aClass);
        if (t != null) {
          return t;
        }
      }

      ExceptionUtilRt.rethrowUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  private static @Nullable <T> T createAsDataClass(@NotNull Class<T> aClass) {
    // support Kotlin data classes - pass null as default value
    for (Annotation annotation : aClass.getAnnotations()) {
      String name = annotation.annotationType().getName();
      if (!name.equals("kotlin.Metadata") && !name.equals("kotlin.jvm.internal.KotlinClass")) {
        continue;
      }

      List<Exception> exceptions = null;
      Constructor<?>[] constructors = aClass.getDeclaredConstructors();
      List<Constructor<?>> defaultCtors = new SmartList<>();
      ctorLoop:
      for (Constructor<?> constructor : constructors) {
        try {
          try {
            constructor.setAccessible(true);
          }
          catch (Throwable ignored) {
          }

          if (constructor.getParameterCount() == 0) {
            //noinspection unchecked
            return (T) constructor.newInstance();
          }

          Class<?>[] parameterTypes = constructor.getParameterTypes();
          for (Class<?> type : parameterTypes) {
            if (type.getName().equals("kotlin.jvm.internal.DefaultConstructorMarker")) {
              defaultCtors.add(constructor);
              continue ctorLoop;
            }
          }

          //noinspection unchecked
          return (T)constructor.newInstance(new Object[parameterTypes.length]);
        }
        catch (Exception e) {
          if (exceptions == null) {
            exceptions = new SmartList<>();
          }
          exceptions.add(new Exception("Failed to call constructor: " + constructor.toString(), e));
        }
      }

      for (Constructor<?> constructor : defaultCtors) {
        try {
          try {
            constructor.setAccessible(true);
          }
          catch (Throwable ignored) {
          }

          //noinspection unchecked
          return (T)constructor.newInstance();
        }
        catch (Exception e) {
          if (exceptions == null) {
            exceptions = new SmartList<>();
          }
          exceptions.add(new Exception("Failed to call constructor: " + constructor.toString(), e));
        }
      }

      if (exceptions != null) {
        if (exceptions.size() == 1) {
          ExceptionUtil.rethrow(exceptions.get(0));
        }
        else {
          ExceptionUtil.rethrow(new CompoundRuntimeException(exceptions));
        }
      }
    }
    return null;
  }

  public static @NotNull <T> T createInstance(@NotNull Constructor<T> constructor, Object @NotNull ... args) {
    try {
      return constructor.newInstance(args);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static @Nullable Class<?> getGrandCallerClass() {
    int stackFrameCount = 3;
    return getCallerClass(stackFrameCount+1);
  }

  public static Class<?> getCallerClass(int stackFrameCount) {
    Class<?> callerClass = findCallerClass(stackFrameCount);
    for (int depth=stackFrameCount+1; callerClass != null && callerClass.getClassLoader() == null; depth++) { // looks like a system class
      callerClass = findCallerClass(depth);
    }
    if (callerClass == null) {
      callerClass = findCallerClass(stackFrameCount-1);
    }
    return callerClass;
  }

  public static void copyFields(Field @NotNull [] fields, @NotNull Object from, @NotNull Object to) {
    copyFields(fields, from, to, null);
  }

  public static void copyFields(Field @NotNull [] fields, @NotNull Object from, @NotNull Object to, @Nullable DifferenceFilter<?> diffFilter) {
    //noinspection SSBasedInspection
    Set<Field> sourceFields = new HashSet<>(Arrays.asList(from.getClass().getFields()));
    for (Field field : fields) {
      if (sourceFields.contains(field)) {
        if (isPublic(field) && !isFinal(field)) {
          try {
            if (diffFilter == null || diffFilter.test(field)) {
              copyFieldValue(from, to, field);
            }
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
  }

  public static <T> boolean comparePublicNonFinalFields(@NotNull T first, @NotNull T second) {
    Class<?> defaultClass = first.getClass();
    Field[] fields = defaultClass.getDeclaredFields();
    if (defaultClass != second.getClass()) {
      fields = ArrayUtil.mergeArrays(fields, second.getClass().getDeclaredFields());
    }
    for (Field field : fields) {
      if (!isPublic(field) || isFinal(field)) {
        continue;
      }

      field.setAccessible(true);
      try {
        if (!Comparing.equal(field.get(second), field.get(first))) {
          return false;
        }
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    return true;
  }

  public static void copyFieldValue(@NotNull Object from, @NotNull Object to, @NotNull Field field)
    throws IllegalAccessException {
    Class<?> fieldType = field.getType();
    if (fieldType.isPrimitive() || fieldType.equals(String.class) || fieldType.isEnum()) {
      field.set(to, field.get(from));
    }
    else {
      throw new RuntimeException("Field '" + field.getName()+"' not copied: unsupported type: "+field.getType());
    }
  }

  private static boolean isPublic(@NotNull Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(@NotNull Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }

  public static @NotNull Class<?> forName(@NotNull String fqn) {
    try {
      return Class.forName(fqn);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static @NotNull Class<?> boxType(@NotNull Class<?> type) {
    if (!type.isPrimitive()) return type;
    if (type == boolean.class) return Boolean.class;
    if (type == byte.class) return Byte.class;
    if (type == short.class) return Short.class;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == float.class) return Float.class;
    if (type == double.class) return Double.class;
    if (type == char.class) return Character.class;
    return type;
  }

  public static @NotNull <T,V> Field getTheOnlyVolatileInstanceFieldOfClass(@NotNull Class<T> ownerClass, @NotNull Class<V> fieldType) {
    Field[] declaredFields = ownerClass.getDeclaredFields();
    Field found = null;
    for (Field field : declaredFields) {
      int modifiers = field.getModifiers();
      if (BitUtil.isSet(modifiers, Modifier.STATIC) || !BitUtil.isSet(modifiers, Modifier.VOLATILE)) {
        continue;
      }
      if (fieldType.isAssignableFrom(field.getType())) {
        if (found == null) {
          found = field;
        }
        else {
          throw new IllegalArgumentException("Two fields of "+fieldType+" found in the "+ownerClass+": "+found + " and "+field);
        }
      }
    }
    if (found == null) {
      throw new IllegalArgumentException("No (non-static, non-final) field of "+fieldType+" found in the "+ownerClass);
    }
    return found;
  }

  private static final Object unsafe;
  static {
    Class<?> unsafeClass;
    try {
      unsafeClass = Class.forName("sun.misc.Unsafe");
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    unsafe = getStaticFieldValue(unsafeClass, unsafeClass, "theUnsafe");
    if (unsafe == null) {
      throw new RuntimeException("Could not find 'theUnsafe' field in the Unsafe class");
    }
  }

  /**
   * @deprecated Use {@link java.lang.invoke.VarHandle} or {@link java.util.concurrent.ConcurrentHashMap} or other standard JDK concurrent facilities
   */
  @ApiStatus.Internal
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull Object getUnsafe() {
    return unsafe;
  }

  /**
   * Returns the class this method was called 'framesToSkip' frames up the caller hierarchy.
   * NOTE:
   * <b>Extremely expensive!
   * Please consider not using it.
   * These aren't the droids you're looking for!</b>
   */
  public static Class<?> findCallerClass(int framesToSkip) {
    return ReflectionUtilRt.findCallerClass(framesToSkip + 1);
  }

  public static boolean isAssignable(@NotNull Class<?> ancestor, @NotNull Class<?> descendant) {
    return ancestor == descendant || ancestor.isAssignableFrom(descendant);
  }

  /**
   * @return concatenated list of field names and values from the {@code object}.
   */
  public static String dumpFields(@NotNull Class<?> objectClass, @Nullable Object object, String... fieldNames) {
    List<String> chunks = new SmartList<>();
    for (String fieldName : fieldNames) {
      chunks.add(fieldName + "=" + getField(objectClass, object, null, fieldName));
    }
    return String.join("; ", chunks);
  }

  /**
   * A convenience type-safe method to create a {@link Proxy} with a single superinterface using the classloader of the specified
   * super-interface.
   * 
   * @param superInterface super-interface
   * @param handler invocation handler to handle method calls
   * @return new proxy instance
   * @param <T> type of the interface to implement
   * @see Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandler) 
   */
  public static <T> @NotNull T proxy(@NotNull Class<? extends T> superInterface, @NotNull InvocationHandler handler) {
    return superInterface.cast(Proxy.newProxyInstance(superInterface.getClassLoader(), new Class[]{superInterface}, handler));
  }

  /**
   * A convenience type-safe method to create a {@link Proxy} with a single superinterface
   * 
   * @param loader classloader to use
   * @param superInterface super-interface
   * @param handler invocation handler to handle method calls
   * @return new proxy instance
   * @param <T> type of the interface to implement
   * @see Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandler) 
   */
  public static <T> @NotNull T proxy(@Nullable ClassLoader loader, @NotNull Class<? extends T> superInterface, @NotNull InvocationHandler handler) {
    return superInterface.cast(Proxy.newProxyInstance(loader, new Class[]{superInterface}, handler));
  }
}