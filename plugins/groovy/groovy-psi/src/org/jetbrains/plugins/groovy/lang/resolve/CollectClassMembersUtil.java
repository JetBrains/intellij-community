/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;

import java.util.*;

/**
 * @author ven
 */
public class CollectClassMembersUtil {
  private static class ClassMembers {
    private final Map<String, CandidateInfo> myFields;
    private final Map<String, List<CandidateInfo>> myMethods;
    private final Map<String, CandidateInfo> myInnerClasses;

    private ClassMembers(@NotNull Map<String, CandidateInfo> fields,
                         @NotNull Map<String, List<CandidateInfo>> methods,
                         @NotNull Map<String, CandidateInfo> innerClasses) {
      myFields = fields;
      myMethods = methods;
      myInnerClasses = innerClasses;
    }

    public static ClassMembers create(@NotNull LinkedHashMap<String, CandidateInfo> first,
                                      @NotNull LinkedHashMap<String, List<CandidateInfo>> second,
                                      @NotNull LinkedHashMap<String, CandidateInfo> third) {
      return new ClassMembers(first, second, third);
    }

    private Map<String, CandidateInfo> getFields() {
      return myFields;
    }

    private Map<String, List<CandidateInfo>> getMethods() {
      return myMethods;
    }

    private Map<String, CandidateInfo> getInnerClasses() {
      return myInnerClasses;
    }
  }

  private static final Key<CachedValue<ClassMembers>> CACHED_MEMBERS = Key.create("CACHED_CLASS_MEMBERS");

  private static final Key<CachedValue<ClassMembers>> CACHED_MEMBERS_INCLUDING_SYNTHETIC = Key.create("CACHED_MEMBERS_INCLUDING_SYNTHETIC");

  private CollectClassMembersUtil() {
  }


  public static Map<String, List<CandidateInfo>> getAllMethods(final PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).getMethods();
  }

  @NotNull
  private static ClassMembers getCachedMembers(@NotNull PsiClass aClass, boolean includeSynthetic) {
    CachedValue<ClassMembers> cached = aClass.getUserData(getMemberCacheKey(includeSynthetic));
    if (cached != null && cached.hasUpToDateValue()) {
      return cached.getValue();
    }

    return buildCache(aClass, includeSynthetic && checkClass(aClass));
  }

  private static boolean checkClass(PsiClass aClass) {
    Set<PsiClass> visited = ContainerUtil.newHashSet();
    Queue<PsiClass> queue = ContainerUtil.newLinkedList(aClass);

    while (!queue.isEmpty()) {
      PsiClass current = queue.poll();
      if (current instanceof ClsClassImpl) continue;
      if (visited.add(current)) {
        if (TransformationUtilKt.isUnderTransformation(current)) return false;
        for (PsiClass superClass : getSupers(current, true)) {
          queue.offer(superClass);
        }
      }
      else if (!current.isInterface() && !CommonClassNames.JAVA_LANG_OBJECT.equals(current.getQualifiedName())) {
        return false;
      }
    }

    return true;
  }

  public static Map<String, CandidateInfo> getAllInnerClasses(@NotNull final PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).getInnerClasses();
  }

  public static Map<String, CandidateInfo> getAllFields(@NotNull final PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).getFields();
  }

  public static Map<String, CandidateInfo> getAllFields(@NotNull final PsiClass aClass) {
    return getAllFields(aClass, true);
  }

  private static ClassMembers buildCache(@NotNull final PsiClass aClass, final boolean includeSynthetic) {
    return CachedValuesManager.getManager(aClass.getProject()).getCachedValue(aClass, getMemberCacheKey(includeSynthetic), () -> {
      LinkedHashMap<String, CandidateInfo> allFields = ContainerUtil.newLinkedHashMap();
      LinkedHashMap<String, List<CandidateInfo>> allMethods = ContainerUtil.newLinkedHashMap();
      LinkedHashMap<String, CandidateInfo> allInnerClasses = ContainerUtil.newLinkedHashMap();

      processClass(aClass, allFields, allMethods, allInnerClasses, new HashSet<>(), PsiSubstitutor.EMPTY, includeSynthetic);
      return CachedValueProvider.Result.create(
        ClassMembers.create(allFields, allMethods, allInnerClasses), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT
      );
    }, false);
  }

  private static Key<CachedValue<ClassMembers>> getMemberCacheKey(boolean includeSynthetic) {
    return includeSynthetic ? CACHED_MEMBERS_INCLUDING_SYNTHETIC : CACHED_MEMBERS;
  }

  private static void processClass(@NotNull PsiClass aClass,
                                   @NotNull Map<String, CandidateInfo> allFields,
                                   @NotNull Map<String, List<CandidateInfo>> allMethods,
                                   @NotNull Map<String, CandidateInfo> allInnerClasses,
                                   @NotNull Set<PsiClass> visitedClasses,
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
        else if (hasExplicitVisibilityModifiers(field)) {
          final CandidateInfo candidateInfo = allFields.get(name);
          final PsiElement element = candidateInfo.getElement();
          if (element instanceof GrField) {
            final GrModifierList modifierList = ((GrField)element).getModifierList();
            if ((modifierList == null || !modifierList.hasExplicitVisibilityModifiers()) &&
                aClass == ((GrField)element).getContainingClass()) {
              //replace property-field with field with explicit visibilityModifier
              allFields.put(name, new CandidateInfo(field, substitutor));
            }
          }
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
      final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, substitutor);
      processClass(superClass, allFields, allMethods, allInnerClasses, visitedClasses, superSubstitutor, includeSynthetic);
    }
  }

  public static PsiField[] getFields(@NotNull PsiClass aClass, boolean includeSynthetic) {
    return includeSynthetic || !(aClass instanceof GrTypeDefinition) ? aClass.getFields() : ((GrTypeDefinition)aClass).getCodeFields();
  }

  public static PsiMethod[] getMethods(@NotNull PsiClass aClass, boolean includeSynthetic) {
    return includeSynthetic || !(aClass instanceof GrTypeDefinition) ? aClass.getMethods() : ((GrTypeDefinition)aClass).getCodeMethods();
  }

  public static PsiClass[] getInnerClasses(@NotNull PsiClass aClass, boolean includeSynthetic) {
    return includeSynthetic || !(aClass instanceof GrTypeDefinition)
           ? aClass.getInnerClasses()
           : ((GrTypeDefinition)aClass).getCodeInnerClasses();
  }

  public static PsiClass[] getSupers(@NotNull PsiClass aClass, boolean includeSynthetic) {
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
