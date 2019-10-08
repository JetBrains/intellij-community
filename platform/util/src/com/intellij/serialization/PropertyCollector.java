// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import com.intellij.openapi.util.Couple;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public class PropertyCollector {
  private final ConcurrentMap<Class<?>, List<MutableAccessor>> classToOwnFields = ContainerUtil.newConcurrentMap();

  private final boolean collectAccessors;
  private final boolean collectPrivateFields;
  private final boolean collectFinalFields;

  public static final byte COLLECT_ACCESSORS = 0x01;
  /**
   * Annotated private field is collected regardless of this flag.
   */
  public static final byte COLLECT_PRIVATE_FIELDS = 0x02;
  /**
   * Annotated field, or if type is Collection or Map, is collected regardless of this flag.
   */
  public static final byte COLLECT_FINAL_FIELDS = 0x04;

  public PropertyCollector(@MagicConstant(flags = {COLLECT_ACCESSORS, COLLECT_PRIVATE_FIELDS, COLLECT_FINAL_FIELDS}) byte flags) {
    this.collectAccessors = BitUtil.isSet(flags, COLLECT_ACCESSORS);
    this.collectPrivateFields = BitUtil.isSet(flags, COLLECT_PRIVATE_FIELDS);
    this.collectFinalFields = BitUtil.isSet(flags, COLLECT_FINAL_FIELDS);
  }

  /**
   * Result is not cached because caller should cache it if need.
   */
  @NotNull
  public List<MutableAccessor> collect(@NotNull Class<?> aClass) {
    List<MutableAccessor> accessors;
    accessors = new ArrayList<>();

    Map<String, Couple<Method>> nameToAccessors;
    // special case for Rectangle.class to avoid infinite recursion during serialization due to bounds() method
    if (!collectAccessors || aClass == Rectangle.class) {
      nameToAccessors = Collections.emptyMap();
    }
    else {
      nameToAccessors = collectPropertyAccessors(aClass, accessors);
    }

    int propertyAccessorCount = accessors.size();
    collectFieldAccessors(aClass, accessors);

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

  private void collectFieldAccessors(@NotNull Class<?> originalClass, @NotNull List<? super MutableAccessor> totalProperties) {
    Class<?> currentClass = originalClass;
    do {
      List<MutableAccessor> ownFields = classToOwnFields.get(currentClass);
      if (ownFields != null) {
        if (!ownFields.isEmpty()) {
          totalProperties.addAll(ownFields);
        }
        continue;
      }

      for (Field field : currentClass.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
          continue;
        }

        if (!hasStoreAnnotations(field)) {
          if (!collectPrivateFields && !(Modifier.isPublic(modifiers))) {
            continue;
          }

          if (!collectFinalFields && Modifier.isFinal(modifiers)) {
            Class<?> fieldType = field.getType();
            // we don't want to allow final fields of all types, but only supported
            if (!(Collection.class.isAssignableFrom(fieldType) || Map.class.isAssignableFrom(fieldType))) {
              continue;
            }
          }

          if (isAnnotatedAsTransient(field)) {
            continue;
          }
        }

        if (ownFields == null) {
          ownFields = new ArrayList<>();
        }
        ownFields.add(new FieldAccessor(field));
      }

      classToOwnFields.putIfAbsent(currentClass, ContainerUtil.notNullize(ownFields));
      if (ownFields != null) {
        totalProperties.addAll(ownFields);
      }
    }
    while ((currentClass = currentClass.getSuperclass()) != null && !isAnnotatedAsTransient(currentClass) && currentClass != Object.class);
  }

  @NotNull
  private Map<String, Couple<Method>> collectPropertyAccessors(@NotNull Class<?> aClass, @NotNull List<? super MutableAccessor> accessors) {
    // (name,(getter,setter))
    final Map<String, Couple<Method>> candidates = new TreeMap<>();
    for (Method method : aClass.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) {
        continue;
      }

      NameAndIsSetter propertyData = getPropertyData(method.getName());
      if (propertyData == null || propertyData.name.equals("class") ||
          method.getParameterTypes().length != (propertyData.isSetter ? 1 : 0)) {
        continue;
      }

      Couple<Method> candidate = candidates.get(propertyData.name);
      if (candidate == null) {
        candidate = Couple.getEmpty();
      }
      if ((propertyData.isSetter ? candidate.second : candidate.first) != null) {
        continue;
      }
      candidate = new Couple<>(propertyData.isSetter ? candidate.first : method, propertyData.isSetter ? method : candidate.second);
      candidates.put(propertyData.name, candidate);
    }

    for (Iterator<Map.Entry<String, Couple<Method>>> iterator = candidates.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, Couple<Method>> candidate = iterator.next();
      Couple<Method> methods = candidate.getValue();
      Method getter = methods.first;
      Method setter = methods.second;
      if (isAcceptableProperty(getter, setter)) {
        accessors.add(new PropertyAccessor(candidate.getKey(), getter.getReturnType(), getter, setter));
      }
      else {
        iterator.remove();
      }
    }
    return candidates;
  }

  protected void clearSerializationCaches() {
    classToOwnFields.clear();
  }

  @Nullable
  private static NameAndIsSetter getPropertyData(@NotNull String methodName) {
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

  @NotNull
  private static String decapitalize(@NotNull String name) {
    if (name.isEmpty() || ((name.length() > 1) && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0)))) {
      return name;
    }

    char[] chars = name.toCharArray();
    chars[0] = Character.toLowerCase(name.charAt(0));
    return new String(chars);
  }

  private boolean isAcceptableProperty(@Nullable Method getter, @Nullable Method setter) {
    if (getter == null || isAnnotatedAsTransient(getter)) {
      return false;
    }

    if (setter == null) {
      // check hasStoreAnnotations to ensure that this addition will not lead to regression (since there is a chance that there is some existing not-annotated list getters without setter)
      return (Collection.class.isAssignableFrom(getter.getReturnType()) || Map.class.isAssignableFrom(getter.getReturnType())) && hasStoreAnnotations(getter);
    }

    if (isAnnotatedAsTransient(setter) || !getter.getReturnType().equals(setter.getParameterTypes()[0])) {
      return false;
    }

    return true;
  }

  protected boolean isAnnotatedAsTransient(@NotNull AnnotatedElement element) {
    return false;
  }

  protected boolean hasStoreAnnotations(@NotNull AccessibleObject element) {
    return false;
  }

  private static final class NameAndIsSetter {
    final String name;
    final boolean isSetter;

    NameAndIsSetter(String name, boolean isSetter) {
      this.name = name;
      this.isSetter = isSetter;
    }
  }
}