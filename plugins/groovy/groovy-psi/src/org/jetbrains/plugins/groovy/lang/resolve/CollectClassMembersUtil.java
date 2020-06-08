// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;

/**
 * @author ven
 */
public final class CollectClassMembersUtil {

  private static class ClassMembers {
    private final Map<String, CandidateInfo> fields = new LinkedHashMap<>();
    private final Map<String, List<CandidateInfo>> methods = new LinkedHashMap<>();
    private final Map<String, CandidateInfo> innerClasses = new LinkedHashMap<>();
  }

  private static final Key<CachedValue<ClassMembers>> CACHED_MEMBERS = Key.create("CACHED_CLASS_MEMBERS");
  private static final Key<CachedValue<ClassMembers>> CACHED_MEMBERS_INCLUDING_SYNTHETIC = Key.create("CACHED_MEMBERS_INCLUDING_SYNTHETIC");

  private CollectClassMembersUtil() {}

  @NotNull
  public static Map<String, List<CandidateInfo>> getAllMethods(@NotNull PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).methods;
  }

  @NotNull
  private static ClassMembers getCachedMembers(@NotNull PsiClass aClass, boolean includeSynthetic) {
    CachedValue<ClassMembers> cached = aClass.getUserData(getMemberCacheKey(includeSynthetic));
    if (cached != null && cached.hasUpToDateValue()) {
      return cached.getValue();
    }

    return buildCache(aClass, includeSynthetic && checkClass(aClass));
  }

  private static boolean checkClass(@NotNull PsiClass aClass) {
    Set<PsiClass> visited = new HashSet<>();
    Queue<PsiClass> queue = ContainerUtil.newLinkedList(aClass);

    while (!queue.isEmpty()) {
      PsiClass current = queue.remove();
      if (current instanceof ClsClassImpl) continue;
      if (visited.add(current)) {
        for (PsiClass superClass : getSupers(current, false)) {
          queue.offer(superClass);
        }
      }
      else if (!current.isInterface() && !CommonClassNames.JAVA_LANG_OBJECT.equals(current.getQualifiedName())) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  public static Map<String, CandidateInfo> getAllInnerClasses(@NotNull final PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).innerClasses;
  }

  @NotNull
  public static Map<String, CandidateInfo> getAllFields(@NotNull final PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).fields;
  }

  @NotNull
  public static Map<String, CandidateInfo> getAllFields(@NotNull final PsiClass aClass) {
    return getAllFields(aClass, true);
  }

  @NotNull
  private static ClassMembers buildCache(@NotNull final PsiClass aClass, final boolean includeSynthetic) {
    return CachedValuesManager.getCachedValue(aClass, getMemberCacheKey(includeSynthetic), () -> {
      ClassMembers result = new ClassMembers();
      processClass(aClass, result.fields, result.methods, result.innerClasses, new HashSet<>(), PsiSubstitutor.EMPTY, includeSynthetic);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @NotNull
  private static Key<CachedValue<ClassMembers>> getMemberCacheKey(boolean includeSynthetic) {
    return includeSynthetic ? CACHED_MEMBERS_INCLUDING_SYNTHETIC : CACHED_MEMBERS;
  }

  private static void processClass(@NotNull PsiClass aClass,
                                   @NotNull Map<String, CandidateInfo> allFields,
                                   @NotNull Map<String, List<CandidateInfo>> allMethods,
                                   @NotNull Map<String, CandidateInfo> allInnerClasses,
                                   @NotNull Set<? super PsiClass> visitedClasses,
                                   @NotNull PsiSubstitutor substitutor,
                                   boolean includeSynthetic) {
    PsiUtilCore.ensureValid(aClass);

    if (!visitedClasses.add(aClass)) return;

    if (visitedClasses.size() == 1 || !GrTraitUtil.isTrait(aClass)) {
      for (PsiField field : getFields(aClass, includeSynthetic)) {
        String name = field.getName();
        if (!allFields.containsKey(name)) {
          allFields.put(name, new CandidateInfo(field, substitutor));
        }
      }
    }

    for (PsiMethod method : getMethods(aClass, includeSynthetic)) {
      addMethod(allMethods, method, substitutor);
    }

    for (final PsiClass inner : getInnerClasses(aClass, includeSynthetic)) {
      final String name = inner.getName();
      if (name != null && !allInnerClasses.containsKey(name)) {
        allInnerClasses.put(name, new CandidateInfo(inner, substitutor));
      }
    }

    for (PsiClass superClass : getSupers(aClass, includeSynthetic)) {
      final PsiSubstitutor superSubstitutor = includeSynthetic
                                              ? TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, substitutor)
                                              : PsiSubstitutor.EMPTY;
      processClass(superClass, allFields, allMethods, allInnerClasses, visitedClasses, superSubstitutor, includeSynthetic);
    }
  }

  private static PsiField @NotNull [] filterProperties(PsiField[] fields) {
    if (fields.length == 0) return PsiField.EMPTY_ARRAY;

    final List<String> fieldNamesList = mapNotNull(fields, it -> hasExplicitVisibilityModifiers(it) ? it.getName() : null);
    if (fieldNamesList.isEmpty()) return fields;

    final Set<String> fieldNames = new HashSet<>(fieldNamesList);
    return filter(fields, it -> hasExplicitVisibilityModifiers(it) || !fieldNames.remove(it.getName())).toArray(PsiField.EMPTY_ARRAY);
  }

  public static PsiField @NotNull [] getFields(@NotNull PsiClass aClass, boolean includeSynthetic) {
    PsiField[] fields = includeSynthetic || !(aClass instanceof GrTypeDefinition)
                        ? aClass.getFields()
                        : ((GrTypeDefinition)aClass).getCodeFields();
    return filterProperties(fields);
  }

  public static PsiMethod @NotNull [] getMethods(@NotNull PsiClass aClass, boolean includeSynthetic) {
    return includeSynthetic || !(aClass instanceof GrTypeDefinition) ? aClass.getMethods() : ((GrTypeDefinition)aClass).getCodeMethods();
  }

  public static PsiClass @NotNull [] getInnerClasses(@NotNull PsiClass aClass, boolean includeSynthetic) {
    return includeSynthetic || !(aClass instanceof GrTypeDefinition)
           ? aClass.getInnerClasses()
           : ((GrTypeDefinition)aClass).getCodeInnerClasses();
  }

  public static PsiClass @NotNull [] getSupers(@NotNull PsiClass aClass, boolean includeSynthetic) {
    return aClass instanceof GrTypeDefinition
           ? ((GrTypeDefinition)aClass).getSupers(includeSynthetic)
           : aClass.getSupers();
  }

  private static boolean hasExplicitVisibilityModifiers(@NotNull PsiField field) {
    if (field instanceof GrField) {
      final GrModifierList list = (GrModifierList)field.getModifierList();
      return list == null || list.hasExplicitVisibilityModifiers();
    }
    else {
      return true;
    }
  }

  private static void addMethod(@NotNull Map<String, List<CandidateInfo>> allMethods,
                                @NotNull PsiMethod method,
                                @NotNull PsiSubstitutor substitutor) {
    String name = method.getName();
    List<CandidateInfo> methods = allMethods.get(name);
    if (methods == null) {
      methods = new ArrayList<>();
      allMethods.put(name, methods);
    }
    methods.add(new CandidateInfo(method, substitutor));
  }
}
