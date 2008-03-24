/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class CollectClassMembersUtil {
  private static final Key<CachedValue<Pair<Map<String, CandidateInfo>,
                                            Map<String, List<CandidateInfo>>>>> CACHED_MEMBERS = Key.create("CACHED_CLASS_MEMBERS");

  private static final Key<CachedValue<Pair<Map<String, CandidateInfo>,
                                            Map<String, List<CandidateInfo>>>>> CACHED_MEMBERS_INCLUDING_SYNTHETIC = Key.create("CACHED_MEMBERS_INCLUDING_SYNTHETIC");


  public static Map<String, List<CandidateInfo>> getAllMethods(final PsiClass aClass, boolean includeSynthetic) {
    Key<CachedValue<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>>> key = includeSynthetic ?
        CACHED_MEMBERS_INCLUDING_SYNTHETIC : CACHED_MEMBERS;
    CachedValue<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> cachedValue = aClass.getUserData(key);
    if (cachedValue == null) {
      cachedValue = buildCache(aClass, includeSynthetic);
    }

    Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>> value = cachedValue.getValue();
    assert value != null;
    return value.getSecond();
  }

  public static Map<String, CandidateInfo> getAllFields(final PsiClass aClass) {
    CachedValue<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> cachedValue = aClass.getUserData(CACHED_MEMBERS);
    if (cachedValue == null) {
      cachedValue = buildCache(aClass, false);
    }

    Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>> value = cachedValue.getValue();
    assert value != null;
    return value.getFirst();
  }

  private static CachedValue<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> buildCache(final PsiClass aClass, final boolean includeSynthetic) {
    return aClass.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>>() {
      public Result<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>> compute() {
        Map<String, CandidateInfo> allFields = new HashMap<String, CandidateInfo>();
        Map<String, List<CandidateInfo>> allMethods = new HashMap<String, List<CandidateInfo>>();

        processClass(aClass, allFields, allMethods, new HashSet<PsiClass>(), PsiSubstitutor.EMPTY, includeSynthetic);
        return new Result<Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>>(new Pair<Map<String, CandidateInfo>, Map<String, List<CandidateInfo>>>(allFields, allMethods), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    }, false);
  }

  private static void processClass(PsiClass aClass, Map<String, CandidateInfo> allFields, Map<String, List<CandidateInfo>> allMethods, Set<PsiClass> visitedClasses, PsiSubstitutor substitutor, boolean includeSynthetic) {
    if (visitedClasses.contains(aClass)) return;
    visitedClasses.add(aClass);

    for (PsiField field : aClass.getFields()) {
      String name = field.getName();
      if (!allFields.containsKey(name)) {
        allFields.put(name, new CandidateInfo(field, substitutor));
      }
    }

    for (PsiMethod method : includeSynthetic || !(aClass instanceof GrTypeDefinition) ? aClass.getMethods() : ((GrTypeDefinition) aClass).getGroovyMethods()) {
      addMethod(allMethods, method, substitutor);
    }

    for (PsiClassType superType : aClass.getSuperTypes()) {
      PsiClass superClass = superType.resolve();
      if (superClass != null) {
        final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, substitutor);
        processClass(superClass, allFields, allMethods, visitedClasses, superSubstitutor, includeSynthetic);
      }
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
