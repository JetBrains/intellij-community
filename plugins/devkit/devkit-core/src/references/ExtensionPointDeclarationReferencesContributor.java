// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.patterns.PsiClassPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex;
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UExpression;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.uast.UastPatterns.*;
import static com.intellij.psi.UastReferenceRegistrar.registerUastReferenceProvider;

public class ExtensionPointDeclarationReferencesContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    PsiClassPattern keyedExtensionCollectorInheritor = psiClass().inheritorOf(false, KeyedExtensionCollector.class.getName());

    registerUastReferenceProvider(
      registrar,
      injectionHostUExpression()
        .sourcePsiFilter(psi -> PsiUtil.isPluginProject(psi.getProject()))
        .andOr(
          uExpression().callParameter(0,
                                      callExpression().constructor(psiClass().inheritorOf(true, BaseExtensionPointName.class.getName()))),

          uExpression().methodCallParameter(0,
                                            psiMethod().withName("create").withParameterCount(1)
                                              .definedInClass(ExtensionPointName.class.getName())),

          uExpression().callParameter(0,
                                      callExpression().constructor(keyedExtensionCollectorInheritor)),
          uExpression().callParameter(0,
                                      callExpression().withMethodName("super").withResolvedMethod(
                                        psiMethod().constructor(true).definedInClass(keyedExtensionCollectorInheritor),
                                        false))
        ),

      new UastInjectionHostReferenceProvider() {
        @Override
        public PsiReference @NotNull [] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                                      @NotNull PsiLanguageInjectionHost host,
                                                                      @NotNull ProcessingContext context) {
          return new PsiReference[]{new ExtensionPointDeclarationReference(host)};
        }
      }, PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }

  private static class ExtensionPointDeclarationReference extends PsiReferenceBase<PsiElement> implements PluginConfigReference {

    private ExtensionPointDeclarationReference(PsiElement psiElement) {
      super(psiElement, PsiUtil.isIdeaProject(psiElement.getProject()) /* todo remove once all declarations are fixed */);
    }

    @Override
    public boolean isHighlightedWhenSoft() {
      return true;
    }

    @Override
    public @NotNull String getUnresolvedMessagePattern() {
      return DevKitBundle.message("code.convert.extension.point.declaration", getValue());
    }

    @Override
    public @Nullable PsiElement resolve() {
      Project project = getElement().getProject();
      GlobalSearchScope searchScope = PsiManager.getInstance(project).isInProject(getElement()) ?
                                      GlobalSearchScopesCore.projectProductionScope(project) :
                                      PluginRelatedLocatorsUtils.getCandidatesScope(project);
      ExtensionPoint resolved = ExtensionPointIndex.findExtensionPoint(project, searchScope, getValue());
      return resolved != null ? resolved.getXmlTag() : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
      Project project = getElement().getProject();
      List<ExtensionPoint> candidates =
        ExtensionPointIndex.getExtensionPointCandidates(project, GlobalSearchScopesCore.projectProductionScope(project));

      Module currentModule = ModuleUtilCore.findModuleForPsiElement(getElement());

      List<LookupElement> lookupElements = new ArrayList<>();
      for (ExtensionPoint candidate : candidates) {
        Module module = candidate.getModule();
        assert module != null;

        LookupElementBuilder builder = LookupElementBuilder.create(candidate.getXmlTag(), candidate.getEffectiveQualifiedName())
          .withTailText(" (" + candidate.getXmlTag().getContainingFile().getName() + ")")
          .withTypeText(module.getName(), ModuleType.get(module).getIcon(), false).withTypeIconRightAligned(true);

        if (module == currentModule) {
          lookupElements.add(PrioritizedLookupElement.withPriority(builder.bold(), 30));
        }
        else {
          lookupElements.add(builder);
        }
      }
      return lookupElements.toArray();
    }
  }
}
