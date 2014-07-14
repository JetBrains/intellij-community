/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;

import java.util.Collection;

/**
 * @author peter
 */
public abstract class NonCodeMembersContributor {
  public static final ExtensionPointName<NonCodeMembersContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.membersContributor");

  private static volatile MultiMap<String, NonCodeMembersContributor> ourClassSpecifiedContributors;
  private static NonCodeMembersContributor[] ourAllTypeContributors;

  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    throw new RuntimeException("One of two 'processDynamicElements()' methods must be implemented");
  }

  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    processDynamicElements(qualifierType, processor, place, state);
  }

  @Nullable
  protected String getParentClassName() {
    return null;
  }

  private static void ensureInit() {
    if (ourClassSpecifiedContributors != null) return;

    MultiMap<String, NonCodeMembersContributor> contributorMap = new MultiMap<String, NonCodeMembersContributor>();

    for (final NonCodeMembersContributor contributor : EP_NAME.getExtensions()) {
      contributorMap.putValue(contributor.getParentClassName(), contributor);
    }

    Collection<NonCodeMembersContributor> allTypeContributors = contributorMap.remove(null);
    ourAllTypeContributors = allTypeContributors.toArray(new NonCodeMembersContributor[allTypeContributors.size()]);
    ourClassSpecifiedContributors = contributorMap;
  }

  public static boolean runContributors(@NotNull PsiType qualifierType,
                                        @NotNull PsiScopeProcessor processor,
                                        @NotNull PsiElement place,
                                        @NotNull ResolveState state) {

    MyDelegatingScopeProcessor delegatingProcessor = new MyDelegatingScopeProcessor(processor);

    ensureInit();

    final PsiClass aClass = PsiTypesUtil.getPsiClass(qualifierType);

    if (aClass != null) {
      for (String superClassName : ClassUtil.getSuperClassesWithCache(aClass).keySet()) {
        for (NonCodeMembersContributor enhancer : ourClassSpecifiedContributors.get(superClassName)) {
          ProgressManager.checkCanceled();
          enhancer.processDynamicElements(qualifierType, aClass, delegatingProcessor, place, state);
          if (!delegatingProcessor.wantMore) {
            return false;
          }
        }
      }
    }

    for (NonCodeMembersContributor contributor : ourAllTypeContributors) {
      ProgressManager.checkCanceled();
      contributor.processDynamicElements(qualifierType, aClass, delegatingProcessor, place, state);
      if (!delegatingProcessor.wantMore) {
        return false;
      }
    }

    return GroovyDslFileIndex.processExecutors(qualifierType, place, processor, state);
  }

  private static class MyDelegatingScopeProcessor extends DelegatingScopeProcessor {
    public boolean wantMore = true;

    public MyDelegatingScopeProcessor(PsiScopeProcessor delegate) {
      super(delegate);
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
      if (!wantMore) {
        return false;
      }
      wantMore = super.execute(element, state);
      return wantMore;
    }
  }
}
