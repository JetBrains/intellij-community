/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class CollectClassMembersUtil {
  private static final Key<CachedValue<Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>>>> CACHED_MEMBERS = Key.create("CACHED_CLASS_MEMBERS");

  private static final Key<CachedValue<Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>>>> CACHED_MEMBERS_INCLUDING_SYNTHETIC = Key.create("CACHED_MEMBERS_INCLUDING_SYNTHETIC");

  private CollectClassMembersUtil() {
  }


  public static Map<String, List<CandidateInfo>> getAllMethods(final PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).getSecond();
  }

  @NotNull
  private static Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>> getCachedMembers(
    PsiClass aClass,
    boolean includeSynthetic) {
    Key<CachedValue<Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>>>> key =
      includeSynthetic ? CACHED_MEMBERS_INCLUDING_SYNTHETIC : CACHED_MEMBERS;
    CachedValue<Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>>> cachedValue = aClass.getUserData(key);
    if (cachedValue == null) {
      cachedValue = buildCache(aClass, includeSynthetic);
      aClass.putUserData(key, cachedValue);
    }
    return cachedValue.getValue();
  }

  public static Map<String, CandidateInfo> getAllInnerClasses(final PsiClass aClass, boolean includeSynthetic) {
    return getCachedMembers(aClass, includeSynthetic).getThird();
  }

  public static Map<String, CandidateInfo> getAllFields(final PsiClass aClass) {
    return getCachedMembers(aClass, false).getFirst();
  }

  private static CachedValue<Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>>> buildCache(final PsiClass aClass, final boolean includeSynthetic) {
    return CachedValuesManager.getManager(aClass.getProject()).createCachedValue(new CachedValueProvider<Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>>>() {
      public Result<Trinity<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>, Map<String, CandidateInfo>>> compute() {
        Map<String, CandidateInfo> allFields = new HashMap<String, CandidateInfo>();
        Map<String, List<CandidateInfo>> allMethods = new HashMap<String, List<CandidateInfo>>();
        Map<String, CandidateInfo> allInnerClasses = new HashMap<String, CandidateInfo>();

        processClass(aClass, allFields, allMethods, allInnerClasses, new HashSet<PsiClass>(), PsiSubstitutor.EMPTY, includeSynthetic);
        return Result.create(Trinity.create(allFields, allMethods, allInnerClasses), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    }, false);
  }

  private static void processClass(PsiClass aClass, Map<String, CandidateInfo> allFields, Map<String, List<CandidateInfo>> allMethods, Map<String, CandidateInfo> allInnerClasses, Set<PsiClass> visitedClasses, PsiSubstitutor substitutor, boolean includeSynthetic) {
    if (visitedClasses.contains(aClass)) return;
    visitedClasses.add(aClass);

    for (PsiField field : aClass.getFields()) {
      String name = field.getName();
      if (!allFields.containsKey(name)) {
        allFields.put(name, new CandidateInfo(field, substitutor));
      } else if (hasExplicitVisibilityModifiers(field)) {
        final CandidateInfo candidateInfo = allFields.get(name);
        final PsiElement element = candidateInfo.getElement();
        if (element instanceof GrField &&
            !(((GrField)element).getModifierList()).hasExplicitVisibilityModifiers() &&
            aClass == ((GrField)element).getContainingClass()) { //replace property-field with field with explicit visibilityModifier 
          allFields.put(name, new CandidateInfo(field, substitutor));
        }
      }
    }

    for (PsiMethod method : includeSynthetic || !(aClass instanceof GrTypeDefinition) ? aClass.getMethods() : ((GrTypeDefinition) aClass).getGroovyMethods()) {
      addMethod(allMethods, method, substitutor);
    }

    for (final PsiClass inner : aClass.getInnerClasses()) {
      final String name = inner.getName();
      if (name != null && !allInnerClasses.containsKey(name)) {
        allInnerClasses.put(name, new CandidateInfo(inner, substitutor));
      }
    }

    for (PsiClassType superType : aClass.getSuperTypes()) {
      PsiClass superClass = superType.resolve();
      if (superClass != null) {
        final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, substitutor);
        processClass(superClass, allFields, allMethods, allInnerClasses, visitedClasses, superSubstitutor, includeSynthetic);
      }
    }
  }

  private static boolean hasExplicitVisibilityModifiers(PsiField field) {
    if (field instanceof GrField) {
      final GrModifierList list = (GrModifierList)field.getModifierList();
      return list == null || list.hasExplicitVisibilityModifiers();
    }
    else {
      return true;
    }
  }

  private static void addMethod(Map<String, List<CandidateInfo>> allMethods, PsiMethod method, PsiSubstitutor substitutor) {
    String name = method.getName();
    List<CandidateInfo> methods = allMethods.get(name);
    if (methods == null) {
      methods = new ArrayList<CandidateInfo>();
      allMethods.put(name, methods);
      methods.add(new CandidateInfo(method, substitutor));
    } else methods.add(new CandidateInfo(method, substitutor));
  }
}
