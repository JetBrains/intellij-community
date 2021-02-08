// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.util.BitUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public class PropertyCollector {
  private final ClassValue<List<MutableAccessor>> classToOwnFields;

  public static final byte COLLECT_ACCESSORS = 0x01;
  /**
   * Annotated private field is collected regardless of this flag.
   */
  public static final byte COLLECT_PRIVATE_FIELDS = 0x02;
  /**
   * Annotated field, or if type is Collection or Map, is collected regardless of this flag.
   */
  public static final byte COLLECT_FINAL_FIELDS = 0x04;

  private final Configuration configuration;

  public PropertyCollector(@NotNull Configuration configuration) {
    this.configuration = configuration;
    classToOwnFields = new PropertyCollectorListClassValue(configuration);
  }

  public PropertyCollector(@MagicConstant(flags = {COLLECT_ACCESSORS, COLLECT_PRIVATE_FIELDS, COLLECT_FINAL_FIELDS}) byte flags) {
    this(new Configuration(BitUtil.isSet(flags, COLLECT_ACCESSORS),
                           BitUtil.isSet(flags, COLLECT_PRIVATE_FIELDS),
                           BitUtil.isSet(flags, COLLECT_FINAL_FIELDS)));
  }

  /**
   * Result is not cached because caller should cache it if needed.
   */
  public @NotNull List<MutableAccessor> collect(@NotNull Class<?> aClass) {
    return doCollect(aClass, configuration, classToOwnFields);
  }

  public static @NotNull List<MutableAccessor> doCollect(@NotNull Class<?> aClass,
                                                         @NotNull Configuration configuration,
                                                         @Nullable ClassValue<List<MutableAccessor>> classToOwnFields) {
    List<MutableAccessor> accessors = new ArrayList<>();

    Map<String, Pair<Method, Method>> nameToAccessors;
    // special case for Rectangle.class to avoid infinite recursion during serialization due to bounds() method
    if (!configuration.collectAccessors || aClass == Rectangle.class) {
      nameToAccessors = Collections.emptyMap();
    }
    else {
      nameToAccessors = collectPropertyAccessors(aClass, accessors, configuration);
    }

    int propertyAccessorCount = accessors.size();
    Class<?> currentClass = aClass;
    do {
      accessors.addAll(classToOwnFields == null ? doCollectOwnFields(currentClass, configuration) : classToOwnFields.get(currentClass));
    }
    while ((currentClass = currentClass.getSuperclass()) != null && !configuration.isAnnotatedAsTransient(currentClass) &&
           currentClass != Object.class && currentClass != AtomicReference.class); // AtomicReference is a superclass of UserDataHolderBase
                                                                                   // which is a superclass of many serializable objects
                                                                                   // and we mustn't consider AtomicReference.getOpaque etc. as serializable properties

    // if there are field accessor and property accessor, prefer field - Kotlin generates private var and getter/setter, but annotation moved to var, not to getter/setter
    // so, we must remove duplicated accessor
    for (int j = propertyAccessorCount; j < accessors.size(); j++) {
      String name = accessors.get(j).getName();
      if (nameToAccessors.containsKey(name)) {
        for (int i = 0; i < propertyAccessorCount; i++) {
          if (accessors.get(i).getName().equals(name)) {
            accessors.remove(i);
            propertyAccessorCount--;
            //noinspection AssignmentToForLoopParameter
            j--;
            break;
          }
        }
      }
    }

    return accessors;
  }

  private static @NotNull Map<String, Pair<Method, Method>> collectPropertyAccessors(@NotNull Class<?> aClass,
                                                                                     @NotNull List<? super MutableAccessor> accessors,
                                                                                     @NotNull Configuration configuration) {
    // (name,(getter,setter))
    final Map<String, Pair<Method, Method>> candidates = new TreeMap<>();
    for (Method method : aClass.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) {
        continue;
      }

      NameAndIsSetter propertyData = getPropertyData(method.getName());
      if (propertyData == null || method.getParameterCount() != (propertyData.isSetter ? 1 : 0) ||
          propertyData.name.equals("class")) {
        continue;
      }

      Pair<Method, Method> candidate = candidates.get(propertyData.name);
      if (candidate == null) {
        candidate = Couple.getEmpty();
      }
      if ((propertyData.isSetter ? candidate.second : candidate.first) != null) {
        continue;
      }
      candidate = new Couple<>(propertyData.isSetter ? candidate.first : method, propertyData.isSetter ? method : candidate.second);
      candidates.put(propertyData.name, candidate);
    }

    for (Iterator<Map.Entry<String, Pair<Method, Method>>> iterator = candidates.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, Pair<Method, Method>> candidate = iterator.next();
      Pair<Method, Method> methods = candidate.getValue();
      Method getter = methods.first;
      Method setter = methods.second;
      if (isAcceptableProperty(getter, setter, configuration)) {
        accessors.add(new PropertyAccessor(candidate.getKey(), getter.getReturnType(), getter, setter));
      }
      else {
        iterator.remove();
      }
    }
    return candidates;
  }

  private static @Nullable NameAndIsSetter getPropertyData(@NotNull String methodName) {
    String part = "";
    boolean isSetter = false;
    if (methodName.startsWith("get")) {
      part = methodName.substring(3);
    }
    else if (methodName.startsWith("is")) {
      part = methodName.substring(2);
    }
    else if (methodName.startsWith("set")) {
      part = methodName.substring(3);
      isSetter = true;
    }

    if (part.isEmpty()) {
      return null;
    }

    int suffixIndex = part.indexOf('$');
    if (suffixIndex > 0) {
      // ignore special kotlin properties
      if (part.endsWith("$annotations")) {
        return null;
      }
      // see XmlSerializerTest.internalVar
      part = part.substring(0, suffixIndex);
    }
    return new NameAndIsSetter(decapitalize(part), isSetter);
  }

  private static @NotNull String decapitalize(@NotNull String name) {
    if (name.isEmpty() || ((name.length() > 1) && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0)))) {
      return name;
    }

    char[] chars = name.toCharArray();
    chars[0] = Character.toLowerCase(name.charAt(0));
    return new String(chars);
  }

  private static boolean isAcceptableProperty(@Nullable Method getter, @Nullable Method setter, @NotNull Configuration configuration) {
    if (getter == null || configuration.isAnnotatedAsTransient(getter)) {
      return false;
    }
    if (getter.getDeclaringClass() == AtomicReference.class) {
      return false;
    }

    if (setter == null) {
      // check hasStoreAnnotations to ensure that this addition will not lead to regression (since there is a chance that there is some existing not-annotated list getters without setter)
      return (Collection.class.isAssignableFrom(getter.getReturnType()) || Map.class.isAssignableFrom(getter.getReturnType())) &&
             configuration.hasStoreAnnotations(getter);
    }

    if (configuration.isAnnotatedAsTransient(setter) || !getter.getReturnType().equals(setter.getParameterTypes()[0])) {
      return false;
    }

    return true;
  }

  private static final class NameAndIsSetter {
    final String name;
    final boolean isSetter;

    NameAndIsSetter(String name, boolean isSetter) {
      this.name = name;
      this.isSetter = isSetter;
    }
  }

  public static class Configuration {
    private final boolean collectAccessors;
    private final boolean collectPrivateFields;
    private final boolean collectFinalFields;

    public Configuration(boolean collectAccessors, boolean collectPrivateFields, boolean collectFinalFields) {
      this.collectAccessors = collectAccessors;
      this.collectPrivateFields = collectPrivateFields;
      this.collectFinalFields = collectFinalFields;
    }

    public boolean isAnnotatedAsTransient(@NotNull AnnotatedElement element) {
      return false;
    }

    public boolean hasStoreAnnotations(@NotNull AccessibleObject element) {
      return false;
    }
  }

  private static final class PropertyCollectorListClassValue extends ClassValue<List<MutableAccessor>> {
    private final @NotNull PropertyCollector.Configuration configuration;

    private PropertyCollectorListClassValue(@NotNull Configuration configuration) {
      this.configuration = configuration;
    }

    @Override
    protected List<MutableAccessor> computeValue(Class<?> type) {
      return doCollectOwnFields(type, configuration);
    }
  }

  private static @NotNull List<MutableAccessor> doCollectOwnFields(@NotNull Class<?> type, @NotNull Configuration configuration) {
    List<MutableAccessor> result = null;
    for (Field field : type.getDeclaredFields()) {
      int modifiers = field.getModifiers();
      if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
        continue;
      }

      if (!configuration.hasStoreAnnotations(field)) {
        if (!configuration.collectPrivateFields && !(Modifier.isPublic(modifiers))) {
          continue;
        }

        if (!configuration.collectFinalFields && Modifier.isFinal(modifiers)) {
          Class<?> fieldType = field.getType();
          // we don't want to allow final fields of all types, but only supported
          if (!(Collection.class.isAssignableFrom(fieldType) || Map.class.isAssignableFrom(fieldType))) {
            continue;
          }
        }

        if (configuration.isAnnotatedAsTransient(field)) {
          continue;
        }
      }

      if (result == null) {
        result = new ArrayList<>();
      }
      result.add(new FieldAccessor(field));
    }
    return result == null ? Collections.emptyList() : result;
  }
}