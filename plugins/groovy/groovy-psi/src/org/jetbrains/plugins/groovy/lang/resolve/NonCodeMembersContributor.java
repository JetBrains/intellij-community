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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MultiProcessor;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.scope.JavaScopeProcessorEvent.CHANGE_LEVEL;
import static com.intellij.util.containers.ContainerUtil.map;
import static org.jetbrains.plugins.groovy.dgm.DGMMemberContributor.processDgmMethods;
import static org.jetbrains.plugins.groovy.lang.resolve.CategoryMemberContributorKt.processCategoriesInScope;
import static org.jetbrains.plugins.groovy.lang.resolve.noncode.MixinMemberContributor.processClassMixins;

/**
 * <p>
 * Contributor must check if the processor accepts the elements the contributor can offer.
 * Feeding processor with unnecessary elements which are then filtered away slows down the reference resolution.
 * <ul>
 *   <li>Ensure that the element name is right.
 *     <p>
 *       Ask processor for the name: {@code val nameHint = processor.getHint(NameHint.KEY)}. <br/>
 *       If the hint is {@code null} or the the name ({@code nameHint.getName(resolveState)}) is {@code null}
 *       then processor doesn't care about the name, so the contributor is free to feed the processor with whatever elements.
 *       This usually happens when completion is in progress. <br/>
 *       If the name is not {@code null} then the contributor has to feed processor with properly named elements.
 *     </p>
 *     <p>
 *       Usually there will be some cache map (name -> element) in contributor model,
 *       and the contributor will either feed all elements from the cache
 *       (if the name is {@code null}) or get element by name and feed this element.
 *     </p>
 *   </li>
 *   <li>Ensure that the element kind is right after checking the name.
 *     <p>
 *       Ask processor for the kind: {@code val kindHint = processor.getHint(ElementClassHint.KEY)}. <br/>
 *       If there is no hint (i.e. {@code null}), then, again,
 *       the contributor is free to feed processor with whatever elements. <br/>
 *       If there is a hint, then contributor has to check if it accepts fields:
 *       {@code kindHint.shouldProcess(ElementClassHint.DeclarationKind.FIELD)}. <br/>
 *       The same applies for methods: kindHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)
 *     </p>
 *   </li>
 * </ul>
 * <p>
 * The processor's {@link PsiScopeProcessor#execute execute} method returns boolean value.
 * Contributors must use use it to check if the processor was stopped:
 * <pre>
 * if (!processor.execute(element, state)) {
 *   return
 * }
 * </pre>
 *
 * @author peter
 * @see com.intellij.psi.scope.NameHint
 * @see com.intellij.psi.scope.ElementClassHint
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
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    processDynamicElements(qualifierType, processor, place, state);
  }

  protected boolean unwrapMultiprocessor() {
    return true;
  }

  @Nullable
  protected String getParentClassName() {
    return null;
  }

  @NotNull
  protected Collection<String> getClassNames() {
    String className = getParentClassName();
    return className == null ? Collections.emptyList() : Collections.singletonList(className);
  }

  private static void ensureInit() {
    if (ourClassSpecifiedContributors != null) return;

    final Collection<NonCodeMembersContributor> allTypeContributors = new ArrayList<>();
    final MultiMap<String, NonCodeMembersContributor> contributorMap = new MultiMap<>();
    for (final NonCodeMembersContributor contributor : EP_NAME.getExtensions()) {
      Collection<String> fqns = contributor.getClassNames();
      if (fqns.isEmpty()) {
        allTypeContributors.add(contributor);
      }
      else {
        for (String fqn : fqns) {
          contributorMap.putValue(fqn, contributor);
        }
      }
    }
    ourAllTypeContributors = allTypeContributors.toArray(new NonCodeMembersContributor[0]);
    ourClassSpecifiedContributors = contributorMap;
  }

  @NotNull
  private static Iterable<NonCodeMembersContributor> getApplicableContributors(@Nullable PsiClass clazz) {
    final List<NonCodeMembersContributor> result = new ArrayList<>();
    if (clazz != null) {
      for (String superClassName : ClassUtil.getSuperClassesWithCache(clazz).keySet()) {
        result.addAll(ourClassSpecifiedContributors.get(superClassName));
      }
    }
    ContainerUtil.addAll(result, ourAllTypeContributors);
    return result;
  }

  public static boolean runContributors(@NotNull PsiType qualifierType,
                                        @NotNull PsiScopeProcessor processor,
                                        @NotNull PsiElement place,
                                        @NotNull ResolveState state) {
    ensureInit();

    final PsiClass aClass = PsiTypesUtil.getPsiClass(qualifierType);
    if (TransformationUtilKt.isUnderTransformation(aClass)) return true;

    final Iterable<? extends PsiScopeProcessor> unwrappedOriginals = MultiProcessor.allProcessors(processor);
    for (PsiScopeProcessor each : unwrappedOriginals) {
      if (!processClassMixins(qualifierType, each, place, state)) {
        return false;
      }
      if (!processCategoriesInScope(qualifierType, each, place, state)) {
        return false;
      }
      if (!processDgmMethods(qualifierType, each, place, state)) {
        return false;
      }
    }

    final List<MyDelegatingScopeProcessor> wrapped = Collections.singletonList(new MyDelegatingScopeProcessor(processor));
    final List<MyDelegatingScopeProcessor> unwrapped = map(MultiProcessor.allProcessors(processor), MyDelegatingScopeProcessor::new);

    final Iterable<NonCodeMembersContributor> contributors = getApplicableContributors(aClass);
    for (NonCodeMembersContributor contributor : contributors) {
      ProgressManager.checkCanceled();
      processor.handleEvent(CHANGE_LEVEL, null);
      final List<MyDelegatingScopeProcessor> delegates = contributor.unwrapMultiprocessor() ? unwrapped : wrapped;
      for (MyDelegatingScopeProcessor delegatingProcessor : delegates) {
        ProgressManager.checkCanceled();
        contributor.processDynamicElements(qualifierType, aClass, delegatingProcessor, place, state);
        if (!delegatingProcessor.wantMore) {
          return false;
        }
      }
    }

    return true;
  }

  private static class MyDelegatingScopeProcessor extends DelegatingScopeProcessor implements MultiProcessor {
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

    @NotNull
    @Override
    public Iterable<? extends PsiScopeProcessor> getProcessors() {
      return MultiProcessor.allProcessors(getDelegate());
    }
  }
}
