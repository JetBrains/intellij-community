/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CollectionUtils {

  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Set<String> s_allCollectionClassesAndInterfaces =
    new HashSet<String>();
  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Map<String, String> s_interfaceForCollection =
    new HashMap<String, String>();

  static {
    s_allCollectionClassesAndInterfaces.add("java.util.AbstractCollection");
    s_allCollectionClassesAndInterfaces.add("java.util.AbstractList");
    s_allCollectionClassesAndInterfaces.add("java.util.AbstractMap");
    s_allCollectionClassesAndInterfaces.add("java.util.AbstractQueue");
    s_allCollectionClassesAndInterfaces.add("java.util.AbstractSequentialList");
    s_allCollectionClassesAndInterfaces.add("java.util.AbstractSet");
    s_allCollectionClassesAndInterfaces.add("java.util.ArrayList");
    s_allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_COLLECTION);
    s_allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_DICTIONARY);
    s_allCollectionClassesAndInterfaces.add("java.util.HashMap");
    s_allCollectionClassesAndInterfaces.add("java.util.HashSet");
    s_allCollectionClassesAndInterfaces.add("java.util.Hashtable");
    s_allCollectionClassesAndInterfaces.add("java.util.IdentityHashMap");
    s_allCollectionClassesAndInterfaces.add("java.util.LinkedHashMap");
    s_allCollectionClassesAndInterfaces.add("java.util.LinkedHashSet");
    s_allCollectionClassesAndInterfaces.add("java.util.LinkedList");
    s_allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_LIST);
    s_allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_MAP);
    s_allCollectionClassesAndInterfaces.add("java.util.Queue");
    s_allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_SET);
    s_allCollectionClassesAndInterfaces.add("java.util.SortedMap");
    s_allCollectionClassesAndInterfaces.add("java.util.SortedSet");
    s_allCollectionClassesAndInterfaces.add("java.util.Stack");
    s_allCollectionClassesAndInterfaces.add("java.util.TreeMap");
    s_allCollectionClassesAndInterfaces.add("java.util.TreeSet");
    s_allCollectionClassesAndInterfaces.add("java.util.Vector");
    s_allCollectionClassesAndInterfaces.add("java.util.WeakHashMap");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.ArrayList");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Collection");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.HashMap");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.HashSet");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Hashtable");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.LinkedList");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.List");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Map");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Set");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.SortedMap");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.SortedSet");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.TreeMap");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.TreeSet");
    s_allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Vector");

    s_interfaceForCollection.put("ArrayList", "List");
    s_interfaceForCollection.put("EnumMap", "Map");
    s_interfaceForCollection.put("EnumSet", "Set");
    s_interfaceForCollection.put("HashMap", "Map");
    s_interfaceForCollection.put("HashSet", "Set");
    s_interfaceForCollection.put("Hashtable", "Map");
    s_interfaceForCollection.put("IdentityHashMap", "Map");
    s_interfaceForCollection.put("LinkedHashMap", "Map");
    s_interfaceForCollection.put("LinkedHashSet", "Set");
    s_interfaceForCollection.put("LinkedList", "List");
    s_interfaceForCollection.put("PriorityQueue", "Queue");
    s_interfaceForCollection.put("TreeMap", "Map");
    s_interfaceForCollection.put("TreeSet", "SortedSet");
    s_interfaceForCollection.put("Vector", "List");
    s_interfaceForCollection.put("WeakHashMap", "Map");
    s_interfaceForCollection.put("java.util.ArrayList", CommonClassNames.JAVA_UTIL_LIST);
    s_interfaceForCollection.put("java.util.EnumMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.EnumSet", CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put("java.util.HashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.HashSet", CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put("java.util.Hashtable", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.IdentityHashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.LinkedHashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.LinkedHashSet", CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put("java.util.LinkedList", CommonClassNames.JAVA_UTIL_LIST);
    s_interfaceForCollection.put("java.util.PriorityQueue", "java.util.Queue");
    s_interfaceForCollection.put("java.util.TreeMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("java.util.TreeSet", CommonClassNames.JAVA_UTIL_SET);
    s_interfaceForCollection.put("java.util.Vector", CommonClassNames.JAVA_UTIL_LIST);
    s_interfaceForCollection.put("java.util.WeakHashMap", CommonClassNames.JAVA_UTIL_MAP);
    s_interfaceForCollection.put("com.sun.java.util.collections.HashSet", "com.sun.java.util.collections.Set");
    s_interfaceForCollection.put("com.sun.java.util.collections.TreeSet", "com.sun.java.util.collections.Set");
    s_interfaceForCollection.put("com.sun.java.util.collections.Vector", "com.sun.java.util.collections.List");
    s_interfaceForCollection.put("com.sun.java.util.collections.ArrayList", "com.sun.java.util.collections.List");
    s_interfaceForCollection.put("com.sun.java.util.collections.LinkedList", "com.sun.java.util.collections.List");
    s_interfaceForCollection.put("com.sun.java.util.collections.TreeMap", "com.sun.java.util.collections.Map");
    s_interfaceForCollection.put("com.sun.java.util.collections.HashMap", "com.sun.java.util.collections.Map");
    s_interfaceForCollection.put("com.sun.java.util.collections.Hashtable", "com.sun.java.util.collections.Map");
  }

  private CollectionUtils() {
    super();
  }

  @Contract("null -> false")
  public static boolean isConcreteCollectionClass(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass resolved = classType.resolve();
    if (resolved == null) {
      return false;
    }
    return isConcreteCollectionClass(resolved);
  }

  @Contract("null -> false")
  public static boolean isConcreteCollectionClass(PsiClass aClass) {
    if (aClass == null || aClass.isEnum() || aClass.isInterface() || aClass.isAnnotationType() ||
        aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION) &&
        !InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
      return false;
    }
    @NonNls final String name = aClass.getQualifiedName();
    return name != null && name.startsWith("java.util.");
  }

  public static boolean isCollectionClassOrInterface(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass resolved = classType.resolve();
    if (resolved == null) {
      return false;
    }
    return isCollectionClassOrInterface(resolved);
  }

  public static boolean isCollectionClassOrInterface(PsiClass aClass) {
    return isCollectionClassOrInterface(aClass, new HashSet<PsiClass>());
  }

  /**
   * alreadyChecked set to avoid infinite loop in constructs like:
   * class C extends C {}
   */
  private static boolean isCollectionClassOrInterface(
    PsiClass aClass, Set<PsiClass> visitedClasses) {
    if (!visitedClasses.add(aClass)) {
      return false;
    }
    final String className = aClass.getQualifiedName();
    if (s_allCollectionClassesAndInterfaces.contains(className)) {
      return true;
    }
    final PsiClass[] supers = aClass.getSupers();
    for (PsiClass aSuper : supers) {
      if (isCollectionClassOrInterface(aSuper, visitedClasses)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isWeakCollectionClass(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final String typeText = type.getCanonicalText();
    if (typeText == null) {
      return false;
    }
    return "java.util.WeakHashMap".equals(typeText);
  }

  public static boolean isConstantEmptyArray(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC) ||
        !field.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    return isEmptyArray(field);
  }

  public static boolean isEmptyArray(PsiField field) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression =
        (PsiArrayInitializerExpression)initializer;
      final PsiExpression[] initializers =
        arrayInitializerExpression.getInitializers();
      return initializers.length == 0;
    }
    return ExpressionUtils.isZeroLengthArrayConstruction(initializer);
  }

  public static boolean isArrayOrCollectionField(@NotNull PsiField field) {
    final PsiType type = field.getType();
    if (isCollectionClassOrInterface(type)) {
      return true;
    }
    if (!(type instanceof PsiArrayType)) {
      return false;
    }
    // constant empty arrays are ignored.
    return !isConstantEmptyArray(field);
  }

  public static String getInterfaceForClass(String name) {
    final int parameterStart = name.indexOf((int)'<');
    final String baseName;
    if (parameterStart >= 0) {
      baseName = name.substring(0, parameterStart).trim();
    }
    else {
      baseName = name;
    }
    return s_interfaceForCollection.get(baseName);
  }
}