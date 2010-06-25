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
package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author peter
 */
public class NonCodeMembersHolder implements CustomMembersHolder {
  private final GrTypeDefinition myPsiClass;
  private static final Key<CachedValue<ConcurrentFactoryMap<String, NonCodeMembersHolder>>> CACHED_HOLDERS = Key.create("CACHED_HOLDERS");

  public static NonCodeMembersHolder fromText(@NotNull String classText, final PsiFile place) {
    return CachedValuesManager.getManager(place.getProject()).getCachedValue(place, CACHED_HOLDERS, new CachedValueProvider<ConcurrentFactoryMap<String, NonCodeMembersHolder>>() {
      public Result<ConcurrentFactoryMap<String, NonCodeMembersHolder>> compute() {
        final ConcurrentFactoryMap<String, NonCodeMembersHolder> map = new ConcurrentFactoryMap<String, NonCodeMembersHolder>() {
          @Override
          protected NonCodeMembersHolder create(String key) {
            return new NonCodeMembersHolder(key, place);
          }
        };
        return Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
      }
    }, false).get(classText);
  }

  private NonCodeMembersHolder(@NotNull String classText, PsiElement place) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(place.getProject());
    myPsiClass = factory.createGroovyFile("class GroovyEnhanced {\n" + classText + "}", false, place).getTypeDefinitions()[0];
  }

  public boolean processMembers(PsiScopeProcessor processor) {
    final NameHint nameHint = processor.getHint(NameHint.KEY);
    final String expectedName = nameHint == null ? null : nameHint.getName(ResolveState.initial());

    for (PsiMethod method : myPsiClass.getMethods()) {
      if ((expectedName == null || expectedName.equals(method.getName())) && !processor.execute(method, ResolveState.initial())) {
        return false;
      }
    }
    for (final PsiField field : myPsiClass.getFields()) {
      if ((expectedName == null || expectedName.equals(field.getName())) && !processor.execute(field, ResolveState.initial())) return false;
    }
    return true;
  }
}
