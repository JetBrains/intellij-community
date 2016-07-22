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
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;

import java.util.Collection;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

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

    MultiMap<String, NonCodeMembersContributor> contributorMap = new MultiMap<>();

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
    ensureInit();

    final PsiClass aClass = PsiTypesUtil.getPsiClass(qualifierType);
    if (TransformationUtilKt.isUnderTransformation(aClass)) return true;

    List<MyDelegatingScopeProcessor> allDelegates = map(GroovyResolverProcessor.allProcessors(processor), MyDelegatingScopeProcessor::new);

    if (aClass != null) {
      for (String superClassName : ClassUtil.getSuperClassesWithCache(aClass).keySet()) {
        for (NonCodeMembersContributor enhancer : ourClassSpecifiedContributors.get(superClassName)) {
          if (!invokeContributor(qualifierType, place, state, aClass, allDelegates, enhancer)) return false;
        }
      }
    }

    for (NonCodeMembersContributor contributor : ourAllTypeContributors) {
      if (!invokeContributor(qualifierType, place, state, aClass, allDelegates, contributor)) return false;
    }

    return GroovyDslFileIndex.processExecutors(qualifierType, place, processor, state);
  }

  private static boolean invokeContributor(@NotNull PsiType qualifierType,
                                           @NotNull PsiElement place,
                                           @NotNull ResolveState state,
                                           PsiClass aClass,
                                           List<MyDelegatingScopeProcessor> allDelegates,
                                           NonCodeMembersContributor enhancer) {
    ProgressManager.checkCanceled();
    for (MyDelegatingScopeProcessor delegatingProcessor : allDelegates) {
      enhancer.processDynamicElements(qualifierType, aClass, delegatingProcessor, place, state);
      if (!delegatingProcessor.wantMore) {
        return false;
      }
    }
    return true;
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
