// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.references;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedReferenceVisitor;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public final class InjectedReferencesContributor extends PsiReferenceContributor {

  public static boolean isInjected(@Nullable PsiReference reference) {
    if (reference == null) return false;
    return getInjectedReferences(reference.getElement()) != null;
  }

  public static PsiReference @Nullable [] getInjectedReferences(PsiElement element) {
    if (!(element instanceof ContributedReferenceHost) && !(element instanceof PsiLanguageInjectionHost)) return null;
    Pair<PsiReference[], Boolean> info = getInjectionInfo(element);
    if (!info.second) return null;
    return info.first;
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element, final @NotNull ProcessingContext context) {
        return getInjectionInfo(element).first;
      }

      @Override
      public boolean acceptsHints(@NotNull PsiElement element, PsiReferenceService.@NotNull Hints hints) {
        if (hints == PsiReferenceService.Hints.HIGHLIGHTED_REFERENCES) {
          return false;
        }

        return super.acceptsHints(element, hints);
      }
    });
  }

  private static Pair<PsiReference[], Boolean> getInjectionInfo(@NotNull PsiElement element) {
    return CachedValuesManager.getCachedValue(element, () ->
      CachedValueProvider.Result
        .create(doGetReferencesByElement(element, new ProcessingContext()), PsiModificationTracker.MODIFICATION_COUNT));
  }


  private static Pair<PsiReference[], Boolean> doGetReferencesByElement(final @NotNull PsiElement element,
                                                                        final @NotNull ProcessingContext context) {
    final List<PsiReference> references = new SmartList<>();
    Project project = element.getProject();
    Configuration configuration = Configuration.getProjectInstance(project);
    final Ref<Boolean> injected = new Ref<>(Boolean.FALSE);
    for (ReferenceInjector injector : ReferenceInjector.EXTENSION_POINT_NAME.getExtensionList()) {
      Collection<BaseInjection> injections = configuration.getInjectionsByLanguageId(injector.getId());
      for (BaseInjection injection : injections) {
        if (injection.acceptForReference(element)) {
          injected.set(Boolean.TRUE);
          LanguageInjectionSupport support = InjectorUtils.findInjectionSupport(injection.getSupportId());
          element.putUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, support);
          List<TextRange> area = injection.getInjectedArea(element);
          for (TextRange range : area) {
            references.addAll(Arrays.asList(injector.getReferences(element, context, range)));
          }
        }
      }
    }
    if (element instanceof PsiLanguageInjectionHost) {
      final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(project);
      InjectedLanguage language = registry.getLanguageFor((PsiLanguageInjectionHost)element, element.getContainingFile());
      if (language != null) {
        ReferenceInjector injector = ReferenceInjector.findById(language.getID());
        if (injector != null) {
          injected.set(Boolean.TRUE);
          element.putUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, registry.getLanguageInjectionSupport());
          TextRange range = ElementManipulators.getValueTextRange(element);
          references.addAll(Arrays.asList(injector.getReferences(element, context, range)));
        }
      }
      else {
        PsiFile containingFile = element.getContainingFile();
        InjectedLanguageManager
          .getInstance(containingFile.getProject()).enumerateEx(element, containingFile, false, new InjectedReferenceVisitor() {
            @Override
            public void visitInjectedReference(@NotNull ReferenceInjector injector,
                                               @NotNull List<? extends PsiLanguageInjectionHost.Shred> places) {
              injected.set(Boolean.TRUE);
              element.putUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, registry.getLanguageInjectionSupport());
              for (PsiLanguageInjectionHost.Shred place : places) {
                if (place.getHost() == element) {
                  references.addAll(Arrays.asList(injector.getReferences(element, context, place.getRangeInsideHost())));
                }
              }
            }
          });
      }
    }
    return Pair.create(references.toArray(PsiReference.EMPTY_ARRAY), injected.get());
  }
}
