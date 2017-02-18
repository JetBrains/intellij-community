/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import java.util.*;

public class CollectionUtils {

  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Set<String> s_allCollectionClassesAndInterfaces;
  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Map<String, String> s_interfaceForCollection =
    new HashMap<>();

  static {
    final Set<String> allCollectionClassesAndInterfaces = new HashSet<>();
    allCollectionClassesAndInterfaces.add("java.util.AbstractCollection");
    allCollectionClassesAndInterfaces.add("java.util.AbstractList");
    allCollectionClassesAndInterfaces.add("java.util.AbstractMap");
    allCollectionClassesAndInterfaces.add("java.util.AbstractQueue");
    allCollectionClassesAndInterfaces.add("java.util.AbstractSequentialList");
    allCollectionClassesAndInterfaces.add("java.util.AbstractSet");
    allCollectionClassesAndInterfaces.add("java.util.ArrayList");
    allCollectionClassesAndInterfaces.add("java.util.ArrayDeque");
    allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_COLLECTION);
    allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_DICTIONARY);
    allCollectionClassesAndInterfaces.add("java.util.EnumMap");
    allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_HASH_MAP);
    allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_HASH_SET);
    allCollectionClassesAndInterfaces.add("java.util.Hashtable");
    allCollectionClassesAndInterfaces.add("java.util.IdentityHashMap");
    allCollectionClassesAndInterfaces.add("java.util.LinkedHashMap");
    allCollectionClassesAndInterfaces.add("java.util.LinkedHashSet");
    allCollectionClassesAndInterfaces.add("java.util.LinkedList");
    allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_LIST);
    allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_MAP);
    allCollectionClassesAndInterfaces.add("java.util.PriorityQueue");
    allCollectionClassesAndInterfaces.add("java.util.Queue");
    allCollectionClassesAndInterfaces.add(CommonClassNames.JAVA_UTIL_SET);
    allCollectionClassesAndInterfaces.add("java.util.SortedMap");
    allCollectionClassesAndInterfaces.add("java.util.SortedSet");
    allCollectionClassesAndInterfaces.add("java.util.Stack");
    allCollectionClassesAndInterfaces.add("java.util.TreeMap");
    allCollectionClassesAndInterfaces.add("java.util.TreeSet");
    allCollectionClassesAndInterfaces.add("java.util.Vector");
    allCollectionClassesAndInterfaces.add("java.util.WeakHashMap");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ArrayBlockingQueue");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.BlockingDeque");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.BlockingQueue");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ConcurrentHashMap");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ConcurrentLinkedDeque");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ConcurrentLinkedQueue");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ConcurrentMap");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ConcurrentNavigableMap");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ConcurrentSkipListMap");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.ConcurrentSkipListSet");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.CopyOnWriteArrayList");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.CopyOnWriteArraySet");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.DelayQueue");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.LinkedBlockingDeque");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.LinkedBlockingQueue");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.LinkedTransferQueue");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.PriorityBlockingQueue");
    allCollectionClassesAndInterfaces.add("java.util.concurrent.SynchronousQueue");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.ArrayList");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Collection");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.HashMap");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.HashSet");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Hashtable");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.LinkedList");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.List");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Map");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Set");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.SortedMap");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.SortedSet");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.TreeMap");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.TreeSet");
    allCollectionClassesAndInterfaces.add("com.sun.java.util.collections.Vector");
    s_allCollectionClassesAndInterfaces = Collections.unmodifiableSet(allCollectionClassesAndInterfaces);

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

  public static Set<String> getAllCollectionNames() {
    return s_allCollectionClassesAndInterfaces;
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
    return isCollectionClassOrInterface(aClass, new HashSet<>());
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
    return "java.util.WeakHashMap".equals(typeText);
  }

  public static boolean isConstantEmptyArray(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC) ||
        !field.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    return isEmptyArray(field);
  }

  public static boolean isEmptyArray(PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)initializer;
      final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
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