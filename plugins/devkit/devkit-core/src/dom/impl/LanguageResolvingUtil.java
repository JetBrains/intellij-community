// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.completion.CompletionConfidenceEP;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomJavaUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.*;

import javax.swing.*;
import java.util.*;

final class LanguageResolvingUtil {
  private static final String ANY_LANGUAGE_DEFAULT_ID = Language.ANY.getID();

  static Collection<LanguageDefinition> getAllLanguageDefinitions(ConvertContext context) {
    List<LanguageDefinition> languageDefinitions = collectLanguageDefinitions(context);
    ContainerUtil.addIfNotNull(languageDefinitions, createAnyLanguageDefinition(context));
    return languageDefinitions;
  }

  private static List<LanguageDefinition> collectLanguageDefinitions(final ConvertContext context) {
    final Project project = context.getProject();
    final Collection<PsiClass> allLanguages =
      CachedValuesManager.getManager(project).getCachedValue(project, () -> {
        final GlobalSearchScope projectProductionScope = GlobalSearchScopesCore.projectProductionScope(project);
        final GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(project);

        // force finding inside IDEA project first
        PsiClass languageClass = JavaPsiFacade.getInstance(project).findClass(Language.class.getName(), projectProductionScope);
        if (languageClass == null) {
          languageClass = JavaPsiFacade.getInstance(project).findClass(Language.class.getName(), librariesScope);
        }
        if (languageClass == null) {
          return Result.create(Collections.emptyList(), PsiModificationTracker.MODIFICATION_COUNT);
        }

        GlobalSearchScope allScope = projectProductionScope.union(ProjectScope.getLibrariesScope(project));
        Collection<PsiClass> allInheritors = new HashSet<>(ClassInheritorsSearch.search(languageClass, allScope, true).findAll());
        return Result.create(allInheritors, PsiModificationTracker.MODIFICATION_COUNT);
      });
    if (allLanguages.isEmpty()) {
      return new SmartList<>();
    }

    final List<LanguageDefinition> libraryDefinitions = collectLibraryLanguages(context, allLanguages);

    final GlobalSearchScope projectProductionScope = GlobalSearchScopesCore.projectProductionScope(project);
    final Collection<PsiClass> projectLanguages =
      ContainerUtil.filter(allLanguages, aClass -> PsiSearchScopeUtil.isInScope(projectProductionScope, aClass));
    final List<LanguageDefinition> projectDefinitions = collectProjectLanguages(projectLanguages, libraryDefinitions);

    final List<LanguageDefinition> all = new ArrayList<>(libraryDefinitions);
    all.addAll(projectDefinitions);
    return all;
  }

  private static List<LanguageDefinition> collectLibraryLanguages(final ConvertContext context,
                                                                  final Collection<PsiClass> allLanguages) {
    return ContainerUtil.mapNotNull(Language.getRegisteredLanguages(), (NullableFunction<Language, LanguageDefinition>)language -> {
      if (language.getID().isEmpty()) {
        return null;
      }
      final PsiClass psiClass = DomJavaUtil.findClass(language.getClass().getName(), context.getInvocationElement(), true);
      if (psiClass == null || !allLanguages.contains(psiClass)) {
        return null;
      }
      final LanguageFileType type = language.getAssociatedFileType();
      final Icon icon = type != null ? type.getIcon() : null;
      return new LanguageDefinition(language.getID(), psiClass, icon, language.getDisplayName());
    });
  }

  private static List<LanguageDefinition> collectProjectLanguages(final Collection<PsiClass> projectLanguages,
                                                                  final List<LanguageDefinition> libraryLanguages) {
    return ContainerUtil.mapNotNull(projectLanguages, (NullableFunction<PsiClass, LanguageDefinition>)language -> {
      if (language.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return null;
      }

      if (ContainerUtil.exists(libraryLanguages, definition -> definition.clazz.equals(language))) {
        return null;
      }

      return CachedValuesManager.getCachedValue(language, () -> {
        String languageId = computeConstantSuperCtorCallParameter(language, 0);
        if (languageId == null) {
          languageId = computeConstantSuperCtorCallParameter(language, 1);
        }
        if (languageId == null) {
          languageId = computeConstantReturnValue(language, "getID");
        }
        if (StringUtil.isEmpty(languageId)) {
          return Result.create(null, language);
        }

        String displayName = computeConstantReturnValue(language, "getDisplayName");

        return Result.createSingleDependency(new LanguageDefinition(languageId, language, null, displayName), language);
      });
    });
  }

  @Nullable
  private static String computeConstantReturnValue(PsiClass languagePsiClass,
                                                   String methodName) {
    final PsiMethod[] methods = languagePsiClass.findMethodsByName(methodName, false);
    if (methods.length != 1) {
      return null;
    }

    final UExpression expression = PsiUtil.getReturnedExpression(methods[0]);
    if (expression == null) return null;
    
    return UastUtils.evaluateString(expression);
  }

  private static String computeConstantSuperCtorCallParameter(PsiClass languagePsiClass, int index) {
    UClass languageClass = UastContextKt.toUElement(languagePsiClass, UClass.class);
    if (languageClass == null) return null;
    if (languageClass instanceof UAnonymousClass) {
      return getStringConstantExpression(UastUtils.findContaining(languageClass.getSourcePsi(), UObjectLiteralExpression.class), index);
    }

    UMethod defaultConstructor = null;
    for (UMethod method : languageClass.getMethods()) {
      if (method.isConstructor() && method.getUastParameters().isEmpty()) {
        defaultConstructor = method;
        break;
      }
    }
    if (defaultConstructor == null) {
      return null;
    }

    final UExpression body = defaultConstructor.getUastBody();
    if (!(body instanceof UBlockExpression)) {
      return null;
    }
    final List<UExpression> expressions = ((UBlockExpression)body).getExpressions();

    // super() must be first
    UExpression expression = ContainerUtil.getFirstItem(expressions);

    if (!(expression instanceof UCallExpression)) {
      return null;
    }
    UCallExpression methodCallExpression = (UCallExpression)expression;

    if (!isSuperConstructorCall(methodCallExpression)) {
      return null;
    }
    return getStringConstantExpression(methodCallExpression, index);
  }

  private static boolean isSuperConstructorCall(@Nullable UCallExpression callExpression) {
    if (callExpression == null) return false;
    return callExpression.getKind() == UastCallKind.CONSTRUCTOR_CALL;
  }

  @Nullable
  private static String getStringConstantExpression(@Nullable UCallExpression callExpression, int index) {
    if (callExpression == null) {
      return null;
    }
    UExpression argument = callExpression.getArgumentForParameter(index);
    if (argument == null) {
      return null;
    }
    return UastUtils.evaluateString(argument);
  }

  @Nullable
  private static LanguageDefinition createAnyLanguageDefinition(ConvertContext context) {
    final PsiClass languageClass = DomJavaUtil.findClass(Language.class.getName(), context.getInvocationElement());
    if (languageClass == null) return null;

    String anyLanguageId = calculateAnyLanguageId(context);
    return new LanguageDefinition(anyLanguageId, languageClass, AllIcons.FileTypes.Any_type, "<any language>");
  }

  private static final Set<String> EP_WITH_ANY_LANGUAGE_ID = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(CompletionContributorEP.class.getName(), CompletionConfidenceEP.class.getName())));

  private static String calculateAnyLanguageId(@NotNull ConvertContext context) {
    final Extension extension = context.getInvocationElement().getParentOfType(Extension.class, true);
    if (extension == null) {
      return ANY_LANGUAGE_DEFAULT_ID;
    }
    final ExtensionPoint extensionPoint = extension.getExtensionPoint();
    if (extensionPoint == null) {
      return ANY_LANGUAGE_DEFAULT_ID;
    }

    final GenericAttributeValue<PsiClass> epBeanClass = extensionPoint.getBeanClass();
    if (EP_WITH_ANY_LANGUAGE_ID.contains(epBeanClass.getStringValue())) {
      return "any";
    }

    return ANY_LANGUAGE_DEFAULT_ID;
  }

  static class LanguageDefinition {
    final String id;
    final PsiClass clazz;
    final Icon icon;
    final String displayName;
    final String type;

    LanguageDefinition(@NotNull String id, @NotNull PsiClass clazz, @Nullable Icon icon, @Nullable String displayName) {
      this.id = id;
      this.clazz = clazz;
      this.type = clazz instanceof PsiAnonymousClass
                  ? ((PsiAnonymousClass)clazz).getBaseClassReference().getQualifiedName()
                  : clazz.getQualifiedName();
      this.icon = icon;
      this.displayName = displayName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LanguageDefinition)) return false;
      return clazz.equals(((LanguageDefinition)o).clazz);
    }

    @Override
    public int hashCode() {
      return clazz.hashCode();
    }
  }
}
