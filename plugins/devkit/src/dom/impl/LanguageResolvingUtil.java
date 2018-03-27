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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.completion.CompletionConfidenceEP;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInspection.dataFlow.StringExpressionHelper;
import com.intellij.icons.AllIcons;
import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class LanguageResolvingUtil {
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
        final PsiClass languageClass = JavaPsiFacade.getInstance(project).findClass(Language.class.getName(),
                                                                                    GlobalSearchScope.allScope(project));
        if (languageClass == null) {
          return Result.create(Collections.emptyList(), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }

        final GlobalSearchScope projectProductionScope = GlobalSearchScopesCore.projectProductionScope(project);
        GlobalSearchScope allScope = projectProductionScope.union(ProjectScope.getLibrariesScope(project));
        final Collection<PsiClass> allInheritors = ClassInheritorsSearch.search(languageClass, allScope, true).findAll();
        return Result.create(allInheritors, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      });
    if (allLanguages.isEmpty()) {
      return new SmartList<>();
    }

    final List<LanguageDefinition> libraryDefinitions = collectLibraryLanguages(context, allLanguages);

    final GlobalSearchScope projectProductionScope = GlobalSearchScopesCore.projectProductionScope(project);
    final Collection<PsiClass> projectLanguages =
      ContainerUtil.filter(allLanguages, aClass -> PsiSearchScopeUtil.isInScope(projectProductionScope, aClass));
    final List<LanguageDefinition> projectDefinitions = collectProjectLanguages(projectLanguages, libraryDefinitions);

    final List<LanguageDefinition> all = ContainerUtil.newArrayList(libraryDefinitions);
    all.addAll(projectDefinitions);
    return all;
  }

  private static List<LanguageDefinition> collectLibraryLanguages(final ConvertContext context,
                                                                  final Collection<PsiClass> allLanguages) {
    return ContainerUtil.mapNotNull(Language.getRegisteredLanguages(), (NullableFunction<Language, LanguageDefinition>)language -> {
      if (language.getID().isEmpty() || language instanceof DependentLanguage) {
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
          return Result.create((LanguageDefinition)null, language);
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

    return getStringConstantExpression(methods[0]);
  }

  private static String computeConstantSuperCtorCallParameter(PsiClass languagePsiClass, int index) {
    if (languagePsiClass instanceof PsiAnonymousClass) {
      return getStringConstantExpression(((PsiAnonymousClass)languagePsiClass).getArgumentList(), index);
    }

    PsiMethod defaultConstructor = null;
    for (PsiMethod constructor : languagePsiClass.getConstructors()) {
      if (constructor.getParameterList().getParametersCount() == 0) {
        defaultConstructor = constructor;
        break;
      }
    }
    if (defaultConstructor == null) {
      return null;
    }

    final PsiCodeBlock body = defaultConstructor.getBody();
    if (body == null) {
      return null;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length < 1) {
      return null;
    }

    // super() must be first
    PsiStatement statement = statements[0];
    if (!(statement instanceof PsiExpressionStatement)) {
      return null;
    }
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) {
      return null;
    }
    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    PsiExpressionList expressionList = methodCallExpression.getArgumentList();
    return getStringConstantExpression(expressionList, index);
  }

  @Nullable
  private static String getStringConstantExpression(@Nullable PsiExpressionList expressionList, int index) {
    if (expressionList == null) {
      return null;
    }

    final PsiExpression[] argumentExpressions = expressionList.getExpressions();
    if (argumentExpressions.length < index + 1) {
      return null;
    }

    return getStringConstantExpression(argumentExpressions[index]);
  }

  @Nullable
  private static String getStringConstantExpression(PsiElement psiElement) {
    final Pair<PsiElement, String> pair = StringExpressionHelper.evaluateConstantExpression(psiElement);
    return pair != null ? pair.second : null;
  }

  @Nullable
  private static LanguageDefinition createAnyLanguageDefinition(ConvertContext context) {
    final PsiClass languageClass = DomJavaUtil.findClass(Language.class.getName(), context.getInvocationElement());
    if (languageClass == null) return null;

    String anyLanguageId = calculateAnyLanguageId(context);
    return new LanguageDefinition(anyLanguageId, languageClass, AllIcons.FileTypes.Any_type, "<any language>");
  }

  private static final Set<String> EP_WITH_ANY_LANGUAGE_ID = ContainerUtil.immutableSet(
    CompletionContributorEP.class.getName(),
    CompletionConfidenceEP.class.getName()
  );

  private static String calculateAnyLanguageId(ConvertContext context) {
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
