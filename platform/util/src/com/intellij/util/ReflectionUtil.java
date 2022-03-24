// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.DifferenceFilter;
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

  @Nullable
  public static Type resolveVariable(@NotNull TypeVariable<?> variable, @NotNull Class<?> classType) {
    return resolveVariable(variable, classType, true);
  }

  @Nullable
  public static Type resolveVariable(@NotNull TypeVariable<?> variable, @NotNull Class<?> classType, boolean resolveInInterfacesOnly) {
    Class<?> aClass = getRawType(classType);
    int index = ArrayUtilRt.find(aClass.getTypeParameters(), variable);
    if (index >= 0) {
      return variable;
    }

    final Class<?>[] classes = aClass.getInterfaces();
    final Type[] genericInterfaces = aClass.getGenericInterfaces();
    for (int i = 0; i <= classes.length; i++) {
      Class<?> anInterface;
      if (i < classes.length) {
        anInterface = classes[i];
      }
      else {
        anInterface = aClass.getSuperclass();
        if (resolveInInterfacesOnly || anInterface == null) {
          continue;
        }
      }
      final Type resolved = resolveVariable(variable, anInterface);
      if (resolved instanceof Class || resolved instanceof ParameterizedType) {
        return resolved;
      }
      if (resolved instanceof TypeVariable) {
        final TypeVariable<?> typeVariable = (TypeVariable<?>)resolved;
        index = ArrayUtilRt.find(anInterface.getTypeParameters(), typeVariable);
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
  private  static String declarationToString(@NotNull GenericDeclaration anInterface) {
    return anInterface.toString() + Arrays.asList(anInterface.getTypeParameters()) + " loaded by " + ((Class<?>)anInterface).getClassLoader();
  }

  @NotNull
  public static Class<?> getRawType(@NotNull Type type) {
    if (type instanceof Class) {
      return (Class<?>)type;
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

  public static Type @NotNull [] getActualTypeArguments(@NotNull ParameterizedType parameterizedType) {
    return parameterizedType.getActualTypeArguments();
  }

  @Nullable
  public static Class<?> substituteGenericType(@NotNull Type genericType, @NotNull Type classType) {
    if (genericType instanceof TypeVariable) {
      final Class<?> aClass = getRawType(classType);
      final Type type = resolveVariable((TypeVariable<?>)genericType, aClass);
      if (type instanceof Class) {
        return (Class<?>)type;
      }
      if (type instanceof ParameterizedType) {
        return (Class<?>)((ParameterizedType)type).getRawType();
      }
      if (type instanceof TypeVariable && classType instanceof ParameterizedType) {
        final int index = ArrayUtilRt.find(aClass.getTypeParameters(), type);
        if (index >= 0) {
          return getRawType(getActualTypeArguments((ParameterizedType)classType)[index]);
        }
      }
    }
    else {
      return getRawType(genericType);
    }
    return null;
  }

  @NotNull
  public static List<Field> collectFields(@NotNull Class<?> clazz) {
    List<Field> result = new ArrayList<>();
    for (Class<?> c : JBIterableClassTraverser.classTraverser(clazz)) {
      Collections.addAll(result, c.getDeclaredFields());
    }
    return result;
  }

  @NotNull
  public static Field findField(@NotNull Class<?> clazz, @Nullable final Class<?> type, @NotNull @NonNls final String name) throws NoSuchFieldException {
    Field result = findFieldInHierarchy(clazz, field -> name.equals(field.getName()) && (type == null || field.getType().equals(type)));
    if (result != null) return result;

    throw new NoSuchFieldException("Class: " + clazz + " name: " + name + " type: " + type);
  }

  @NotNull
  public static Field findAssignableField(@NotNull Class<?> clazz, @Nullable("null means any type") final Class<?> fieldType, @NotNull @NonNls String fieldName) throws NoSuchFieldException {
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

  @Nullable
  private static Field processInterfaces(Class<?> @NotNull [] interfaces,
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

  public static void resetField(@Nullable final Object object, @NotNull Field field) {
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

  @Nullable
  public static Method findMethod(@NotNull Collection<Method> methods, @NonNls @NotNull String name, Class<?> @NotNull ... parameters) {
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

  @Nullable
  public static Method getMethod(@NotNull Class<?> aClass, @NonNls @NotNull String name, Class<?> @NotNull ... parameters) {
    try {
      return makeAccessible(aClass.getMethod(name, parameters));
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  @Nullable
  public static Method getDeclaredMethod(@NotNull Class<?> aClass, @NonNls @NotNull String name, Class<?> @NotNull ... parameters) {
    try {
      return makeAccessible(aClass.getDeclaredMethod(name, parameters));
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  @Nullable
  public static Field getDeclaredField(@NotNull Class<?> aClass, @NonNls @NotNull final String name) {
    return findFieldInHierarchy(aClass, field -> name.equals(field.getName()));
  }

  @NotNull
  public static List<Method> getClassPublicMethods(@NotNull Class<?> aClass) {
    return getClassPublicMethods(aClass, false);
  }

  @NotNull
  public static List<Method> getClassPublicMethods(@NotNull Class<?> aClass, boolean includeSynthetic) {
    Method[] methods = aClass.getMethods();
    return includeSynthetic ? Arrays.asList(methods) : filterRealMethods(methods);
  }

  @NotNull
  public static List<Method> getClassDeclaredMethods(@NotNull Class<?> aClass) {
    return getClassDeclaredMethods(aClass, false);
  }

  @NotNull
  public static List<Method> getClassDeclaredMethods(@NotNull Class<?> aClass, boolean includeSynthetic) {
    Method[] methods = aClass.getDeclaredMethods();
    return includeSynthetic ? Arrays.asList(methods) : filterRealMethods(methods);
  }

  @NotNull
  public static List<Field> getClassDeclaredFields(@NotNull Class<?> aClass) {
    Field[] fields = aClass.getDeclaredFields();
    return Arrays.asList(fields);
  }

  @NotNull
  private static List<Method> filterRealMethods(Method @NotNull [] methods) {
    List<Method> result = new ArrayList<>();
    for (Method method : methods) {
      if (!method.isSynthetic()) {
        result.add(method);
      }
    }
    return result;
  }

  @Nullable
  public static Class<?> getMethodDeclaringClass(@NotNull Class<?> instanceClass, @NonNls @NotNull String methodName, Class<?> @NotNull ... parameters) {
    Method method = getMethod(instanceClass, methodName, parameters);
    if (method != null) return method.getDeclaringClass();

    while (instanceClass != null) {
      method = getDeclaredMethod(instanceClass, methodName, parameters);
      if (method != null) return method.getDeclaringClass();

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

  public static Type resolveVariableInHierarchy(@NotNull TypeVariable<?> variable, @NotNull Class<?> aClass) {
    Type type;
    Class<?> current = aClass;
    while ((type = resolveVariable(variable, current, false)) == null) {
      current = current.getSuperclass();
      if (current == null) {
        return null;
      }
    }
    if (type instanceof TypeVariable) {
      return resolveVariableInHierarchy((TypeVariable<?>)type, aClass);
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

  /**
   * Handles private classes.
   */
  @NotNull
  public static <T> T newInstance(@NotNull Class<T> aClass) {
    return newInstance(aClass, true);
  }

  @NotNull
  public static <T> T newInstance(@NotNull Class<T> aClass, boolean isKotlinDataClassesSupported) {
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

  @Nullable
  private static <T> T createAsDataClass(@NotNull Class<T> aClass) {
    // support Kotlin data classes - pass null as default value
    for (Annotation annotation : aClass.getAnnotations()) {
      String name = annotation.annotationType().getName();
      if (!name.equals("kotlin.Metadata") && !name.equals("kotlin.jvm.internal.KotlinClass")) {
        continue;
      }

      Constructor<?>[] constructors = aClass.getDeclaredConstructors();
      Exception exception = null;
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
          exception = e;
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
          exception = e;
        }
      }

      if (exception != null) {
        ExceptionUtil.rethrow(exception);
      }
    }
    return null;
  }

  @NotNull
  public static <T> T createInstance(@NotNull Constructor<T> constructor, Object @NotNull ... args) {
    try {
      return constructor.newInstance(args);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static Class<?> getGrandCallerClass() {
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

  public static boolean copyFields(Field @NotNull [] fields, @NotNull Object from, @NotNull Object to, @Nullable DifferenceFilter<?> diffFilter) {
    //noinspection SSBasedInspection
    Set<Field> sourceFields = new HashSet<>(Arrays.asList(from.getClass().getFields()));
    boolean valuesChanged = false;
    for (Field field : fields) {
      if (sourceFields.contains(field)) {
        if (isPublic(field) && !isFinal(field)) {
          try {
            if (diffFilter == null || diffFilter.test(field)) {
              copyFieldValue(from, to, field);
              valuesChanged = true;
            }
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
    return valuesChanged;
  }

  public static <T> boolean comparePublicNonFinalFields(@NotNull T first, @NotNull T second) {
    Class<?> defaultClass = first.getClass();
    Field[] fields = defaultClass.getDeclaredFields();
    if (defaultClass != second.getClass()) {
      fields = ArrayUtil.mergeArrays(fields, second.getClass().getDeclaredFields());
    }
    for (Field field : fields) {
      if (!isPublic(field) || isFinal(field)) continue;
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

  @NotNull
  public static Class<?> forName(@NotNull String fqn) {
    try {
      return Class.forName(fqn);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static Class<?> boxType(@NotNull Class<?> type) {
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

  @NotNull
  public static <T,V> Field getTheOnlyVolatileInstanceFieldOfClass(@NotNull Class<T> ownerClass, @NotNull Class<V> fieldType) {
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
   *
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
}