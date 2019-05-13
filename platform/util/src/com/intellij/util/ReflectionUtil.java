// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ReflectionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ReflectionUtil");

  private ReflectionUtil() { }

  @Nullable
  public static Type resolveVariable(@NotNull TypeVariable variable, @NotNull Class classType) {
    return resolveVariable(variable, classType, true);
  }

  @Nullable
  public static Type resolveVariable(@NotNull TypeVariable variable, @NotNull Class classType, boolean resolveInInterfacesOnly) {
    final Class aClass = getRawType(classType);
    int index = ArrayUtilRt.find(aClass.getTypeParameters(), variable);
    if (index >= 0) {
      return variable;
    }

    final Class[] classes = aClass.getInterfaces();
    final Type[] genericInterfaces = aClass.getGenericInterfaces();
    for (int i = 0; i <= classes.length; i++) {
      Class anInterface;
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
        final TypeVariable typeVariable = (TypeVariable)resolved;
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
  public static String declarationToString(@NotNull GenericDeclaration anInterface) {
    return anInterface.toString() + Arrays.asList(anInterface.getTypeParameters()) + " loaded by " + ((Class)anInterface).getClassLoader();
  }

  @NotNull
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
    return parameterizedType.getActualTypeArguments();
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
  public static List<Field> collectFields(@NotNull Class clazz) {
    List<Field> result = ContainerUtil.newArrayList();
    for (Class c : classTraverser(clazz)) {
      ContainerUtil.addAll(result, c.getDeclaredFields());
    }
    return result;
  }

  @NotNull
  public static Field findField(@NotNull Class clazz, @Nullable final Class type, @NotNull final String name) throws NoSuchFieldException {
    Field result = findFieldInHierarchy(clazz, new Condition<Field>() {
      @Override
      public boolean value(Field field) {
        return name.equals(field.getName()) && (type == null || field.getType().equals(type));
      }
    });
    if (result != null) return result;

    throw new NoSuchFieldException("Class: " + clazz + " name: " + name + " type: " + type);
  }

  @NotNull
  public static Field findAssignableField(@NotNull Class<?> clazz, @Nullable("null means any type") final Class<?> fieldType, @NotNull final String fieldName) throws NoSuchFieldException {
    Field result = findFieldInHierarchy(clazz, new Condition<Field>() {
      @Override
      public boolean value(Field field) {
        return fieldName.equals(field.getName()) && (fieldType == null || fieldType.isAssignableFrom(field.getType()));
      }
    });
    if (result != null) return result;
    throw new NoSuchFieldException("Class: " + clazz + " fieldName: " + fieldName + " fieldType: " + fieldType);
  }

  @Nullable
  private static Field findFieldInHierarchy(@NotNull Class clazz, @NotNull Condition<? super Field> checker) {
    for (Class c : classTraverser(clazz)) {
      Field field = ContainerUtil.find(c.getDeclaredFields(), checker);
      if (field != null) {
        field.setAccessible(true);
        return field;
      }
    }
    return null;
  }

  public static void resetField(@NotNull Class clazz, @Nullable("null means of any type") Class type, @NotNull String name)  {
    try {
      resetField(null, findField(clazz, type, name));
    }
    catch (NoSuchFieldException e) {
      LOG.info(e);
    }
  }

  public static void resetField(@NotNull Object object, @Nullable("null means any type") Class type, @NotNull String name)  {
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
      LOG.info(e);
    }
  }

  @Nullable
  public static Method findMethod(@NotNull Collection<Method> methods, @NonNls @NotNull String name, @NotNull Class... parameters) {
    for (final Method method : methods) {
      if (name.equals(method.getName()) && Arrays.equals(parameters, method.getParameterTypes())) {
        method.setAccessible(true);
        return method;
      }
    }
    return null;
  }

  @Nullable
  public static Method getMethod(@NotNull Class aClass, @NonNls @NotNull String name, @NotNull Class... parameters) {
    return findMethod(getClassPublicMethods(aClass, false), name, parameters);
  }

  @Nullable
  public static Method getDeclaredMethod(@NotNull Class aClass, @NonNls @NotNull String name, @NotNull Class... parameters) {
    return findMethod(getClassDeclaredMethods(aClass, false), name, parameters);
  }

  @Nullable
  public static Field getDeclaredField(@NotNull Class aClass, @NonNls @NotNull final String name) {
    return findFieldInHierarchy(aClass, new Condition<Field>() {
      @Override
      public boolean value(Field field) {
        return name.equals(field.getName());
      }
    });
  }

  @NotNull
  public static List<Method> getClassPublicMethods(@NotNull Class aClass) {
    return getClassPublicMethods(aClass, false);
  }

  @NotNull
  public static List<Method> getClassPublicMethods(@NotNull Class aClass, boolean includeSynthetic) {
    Method[] methods = aClass.getMethods();
    return includeSynthetic ? Arrays.asList(methods) : filterRealMethods(methods);
  }

  @NotNull
  public static List<Method> getClassDeclaredMethods(@NotNull Class aClass) {
    return getClassDeclaredMethods(aClass, false);
  }

  @NotNull
  public static List<Method> getClassDeclaredMethods(@NotNull Class aClass, boolean includeSynthetic) {
    Method[] methods = aClass.getDeclaredMethods();
    return includeSynthetic ? Arrays.asList(methods) : filterRealMethods(methods);
  }

  @NotNull
  public static List<Field> getClassDeclaredFields(@NotNull Class aClass) {
    Field[] fields = aClass.getDeclaredFields();
    return Arrays.asList(fields);
  }

  @NotNull
  private static List<Method> filterRealMethods(@NotNull Method[] methods) {
    List<Method> result = ContainerUtil.newArrayList();
    for (Method method : methods) {
      if (!method.isSynthetic()) {
        result.add(method);
      }
    }
    return result;
  }

  @Nullable
  public static Class getMethodDeclaringClass(@NotNull Class<?> instanceClass, @NonNls @NotNull String methodName, @NotNull Class... parameters) {
    Method method = getMethod(instanceClass, methodName, parameters);
    return method == null ? null : method.getDeclaringClass();
  }

  public static <T> T getField(@NotNull Class objectClass, @Nullable Object object, @Nullable("null means any type") Class<T> fieldType, @NotNull @NonNls String fieldName) {
    try {
      final Field field = findAssignableField(objectClass, fieldType, fieldName);
      @SuppressWarnings("unchecked") T t = (T)field.get(object);
      return t;
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

  public static <T> T getStaticFieldValue(@NotNull Class objectClass, @Nullable("null means any type") Class<T> fieldType, @NotNull @NonNls String fieldName) {
    try {
      final Field field = findAssignableField(objectClass, fieldType, fieldName);
      if (!Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Field " + objectClass + "." + fieldName + " is not static");
      }
      @SuppressWarnings("unchecked") T t = (T)field.get(null);
      return t;
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

  // returns true if value was set
  public static <T> boolean setField(@NotNull Class objectClass,
                                     Object object,
                                     @Nullable("null means any type") Class<T> fieldType,
                                     @NotNull @NonNls String fieldName,
                                     T value) {
    try {
      final Field field = findAssignableField(objectClass, fieldType, fieldName);
      field.set(object, value);
      return true;
    }
    catch (NoSuchFieldException e) {
      LOG.debug(e);
      // this 'return' was moved into 'catch' block because otherwise reference to common super-class of these exceptions (ReflectiveOperationException)
      // which doesn't exist in JDK 1.6 will be added to class-file during instrumentation
      return false;
    }
    catch (IllegalAccessException e) {
      LOG.debug(e);
      return false;
    }
  }

  public static Type resolveVariableInHierarchy(@NotNull TypeVariable variable, @NotNull Class aClass) {
    Type type;
    Class current = aClass;
    while ((type = resolveVariable(variable, current, false)) == null) {
      current = current.getSuperclass();
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

  /**
   * Like {@link Class#newInstance()} but also handles private classes
   */
  @NotNull
  public static <T> T newInstance(@NotNull Class<T> aClass) {
    try {
      Constructor<T> constructor = aClass.getDeclaredConstructor();
      try {
        constructor.setAccessible(true);
      }
      catch (SecurityException e) {
        return aClass.newInstance();
      }
      return constructor.newInstance();
    }
    catch (Exception e) {
      if (e instanceof InvocationTargetException && ((InvocationTargetException) e).getTargetException() instanceof ProcessCanceledException) {
        throw (ProcessCanceledException) (((InvocationTargetException) e).getTargetException());
      }
      T t = createAsDataClass(aClass);
      if (t != null) {
        return t;
      }

      ExceptionUtil.rethrow(e);
    }

    // error will be thrown
    return null;
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
      List<Constructor<?>> defaultCtors = new SmartList<Constructor<?>>();
      ctorLoop:
      for (Constructor<?> constructor : constructors) {
        try {
          try {
            constructor.setAccessible(true);
          }
          catch (Throwable ignored) {
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
  public static <T> T createInstance(@NotNull Constructor<T> constructor, @NotNull Object... args) {
    try {
      return constructor.newInstance(args);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static Class getGrandCallerClass() {
    int stackFrameCount = 3;
    return getCallerClass(stackFrameCount+1);
  }

  public static Class getCallerClass(int stackFrameCount) {
    Class callerClass = findCallerClass(stackFrameCount);
    for (int depth=stackFrameCount+1; callerClass != null && callerClass.getClassLoader() == null; depth++) { // looks like a system class
      callerClass = findCallerClass(depth);
    }
    if (callerClass == null) {
      callerClass = findCallerClass(stackFrameCount-1);
    }
    return callerClass;
  }

  public static void copyFields(@NotNull Field[] fields, @NotNull Object from, @NotNull Object to) {
    copyFields(fields, from, to, null);
  }

  public static boolean copyFields(@NotNull Field[] fields, @NotNull Object from, @NotNull Object to, @Nullable DifferenceFilter diffFilter) {
    Set<Field> sourceFields = ContainerUtilRt.newHashSet(from.getClass().getFields());
    boolean valuesChanged = false;
    for (Field field : fields) {
      if (sourceFields.contains(field)) {
        if (isPublic(field) && !isFinal(field)) {
          try {
            if (diffFilter == null || diffFilter.isAccept(field)) {
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
    return compareFields(first, second, new Predicate<Field>() {
      @Override
      public boolean apply(Field field) {
        return isPublic(field) && !isFinal(field);
      }
    });
  }

  public static <T> boolean compareFields(@NotNull T defaultSettings, @NotNull T newSettings, @NotNull Predicate<? super Field> useField) {
    Class<?> defaultClass = defaultSettings.getClass();
    Field[] fields = defaultClass.getDeclaredFields();
    if (defaultClass != newSettings.getClass()) {
      fields = ArrayUtil.mergeArrays(fields, newSettings.getClass().getDeclaredFields());
    }
    for (Field field : fields) {
      if (!useField.apply(field)) continue;
      field.setAccessible(true);
      try {
        if (!Comparing.equal(field.get(newSettings), field.get(defaultSettings))) {
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

  private static boolean isPublic(final Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(final Field field) {
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

  public static boolean isAssignable(@NotNull Class<?> ancestor, @NotNull Class<?> descendant) {
    return ancestor == descendant || ancestor.isAssignableFrom(descendant);
  }

  @NotNull
  public static JBTreeTraverser<Class> classTraverser(@Nullable Class root) {
    return CLASS_TRAVERSER.unique().withRoot(root);
  }

  private static final JBTreeTraverser<Class> CLASS_TRAVERSER = JBTreeTraverser.from(new Function<Class, Iterable<Class>>() {
    @Override
    public Iterable<Class> fun(Class aClass) {
      return JBIterable.of(aClass.getSuperclass()).append(aClass.getInterfaces());
    }
  });
}