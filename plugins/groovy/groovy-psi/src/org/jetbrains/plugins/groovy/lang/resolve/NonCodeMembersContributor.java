// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.resolve.processors.MultiProcessor;
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
    ourAllTypeContributors = allTypeContributors.toArray(new NonCodeMembersContributor[0]);
    ourClassSpecifiedContributors = contributorMap;
  }

  public static boolean runContributors(@NotNull PsiType qualifierType,
                                        @NotNull PsiScopeProcessor processor,
                                        @NotNull PsiElement place,
                                        @NotNull ResolveState state) {
    ensureInit();

    final PsiClass aClass = PsiTypesUtil.getPsiClass(qualifierType);
    if (TransformationUtilKt.isUnderTransformation(aClass)) return true;

    List<MyDelegatingScopeProcessor> allDelegates = map(MultiProcessor.allProcessors(processor), MyDelegatingScopeProcessor::new);

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

    return GroovyDslFileIndex.processExecutors(
      qualifierType,
      place,
      (holder, descriptor) -> holder.processMembers(descriptor, processor, state)
    );
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

    MyDelegatingScopeProcessor(PsiScopeProcessor delegate) {
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
