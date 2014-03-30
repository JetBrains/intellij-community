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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.icons.AllIcons;
import com.intellij.lang.DependentLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomJavaUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class LanguageResolvingUtil {

  private static final String ANY_LANGUAGE_DEFAULT_ID = Language.ANY.getID();

  static Collection<LanguageDefinition> getAllLanguageDefinitions(ConvertContext context) {
    List<LanguageDefinition> languageDefinitions = collectLanguageDefinitions(context);
    ContainerUtil.addIfNotNull(languageDefinitions, createAnyLanguageDefinition(context));
    return languageDefinitions;
  }

  private static List<LanguageDefinition> collectLanguageDefinitions(final ConvertContext context) {
    final PsiClass languageClass = DomJavaUtil.findClass(Language.class.getName(), context.getInvocationElement());
    if (languageClass == null) {
      return Collections.emptyList();
    }

    final Project project = context.getProject();
    final GlobalSearchScope projectProductionScope = GlobalSearchScopesCore.projectProductionScope(project);
    GlobalSearchScope allScope = projectProductionScope.union(ProjectScope.getLibrariesScope(project));
    final Collection<PsiClass> allLanguages = ClassInheritorsSearch.search(languageClass,
                                                                           allScope, true).findAll();
    final List<LanguageDefinition> libraryDefinitions = collectLibraryLanguages(context, allLanguages);

    final Collection<PsiClass> projectLanguages = ClassInheritorsSearch.search(languageClass,
                                                                               projectProductionScope, true).findAll();
    final List<LanguageDefinition> projectDefinitions = collectProjectLanguages(projectLanguages, libraryDefinitions);

    final List<LanguageDefinition> all = new ArrayList<LanguageDefinition>(libraryDefinitions);
    all.addAll(projectDefinitions);
    return all;
  }

  private static List<LanguageDefinition> collectLibraryLanguages(final ConvertContext context,
                                                                  final Collection<PsiClass> allLanguages) {
    return ContainerUtil.mapNotNull(Language.getRegisteredLanguages(), new NullableFunction<Language, LanguageDefinition>() {
      @Override
      public LanguageDefinition fun(Language language) {
        if (language.getID().isEmpty() ||
            language instanceof DependentLanguage) {
          return null;
        }
        final PsiClass psiClass = DomJavaUtil.findClass(language.getClass().getName(), context.getInvocationElement());
        if (psiClass == null) {
          return null;
        }

        if (!allLanguages.contains(psiClass)) {
          return null;
        }

        final LanguageFileType type = language.getAssociatedFileType();
        final Icon icon = type != null ? type.getIcon() : null;
        return new LanguageDefinition(language.getID(),
                                      psiClass,
                                      icon, language.getDisplayName());
      }
    });
  }

  private static List<LanguageDefinition> collectProjectLanguages(final Collection<PsiClass> projectLanguages,
                                                                  final List<LanguageDefinition> libraryLanguages) {
    return ContainerUtil.mapNotNull(projectLanguages, new NullableFunction<PsiClass, LanguageDefinition>() {
      @Nullable
      @Override
      public LanguageDefinition fun(final PsiClass language) {
        if (language.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return null;
        }

        if (ContainerUtil.exists(libraryLanguages, new Condition<LanguageDefinition>() {
          @Override
          public boolean value(LanguageDefinition definition) {
            return definition.clazz.equals(language);
          }
        })) {
          return null;
        }

        String id = computeConstantSuperCtorCallParameter(language, 0);
        if (id == null) {
          id = computeConstantSuperCtorCallParameter(language, 1);
        }
        if (id == null) {
          id = computeConstantReturnValue(language, "getID");
        }
        if (id == null) {
          return null;
        }

        return new LanguageDefinition(id,
                                      language,
                                      null,
                                      computeConstantReturnValue(language, "getDisplayName"));
      }
    });
  }

  @Nullable
  private static String computeConstantReturnValue(PsiClass languagePsiClass,
                                                   String methodName) {
    final PsiMethod[] methods = languagePsiClass.findMethodsByName(methodName, false);
    if (methods.length != 1) {
      return null;
    }

    final PsiMethod method = methods[0];
    final PsiCodeBlock body = method.getBody();

    if (body == null) {
      return null;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) {
      return null;
    }


    final PsiStatement statement = statements[0];
    if (!(statement instanceof PsiReturnStatement)) {
      return null;
    }
    final PsiReturnStatement returnStatement =
      (PsiReturnStatement)statement;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null ||
        !PsiUtil.isConstantExpression(returnValue)) {
      return null;
    }

    return computeConstant(languagePsiClass, returnValue);
  }

  private static String computeConstantSuperCtorCallParameter(PsiClass languagePsiClass, int index) {
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
    final PsiExpression[] argumentExpressions = methodCallExpression.getArgumentList().getExpressions();
    if (argumentExpressions.length < index + 1) {
      return null;
    }
    return computeConstant(languagePsiClass, argumentExpressions[index]);
  }

  private static String computeConstant(PsiClass languagePsiClass, PsiExpression returnValue) {
    final PsiConstantEvaluationHelper constantEvaluationHelper =
      JavaPsiFacade.getInstance(languagePsiClass.getProject()).getConstantEvaluationHelper();

    final Object constant = constantEvaluationHelper.computeConstantExpression(returnValue);
    return constant instanceof String ? ((String)constant) : null;
  }

  @Nullable
  private static LanguageDefinition createAnyLanguageDefinition(ConvertContext context) {
    final PsiClass languageClass = DomJavaUtil.findClass(Language.class.getName(), context.getInvocationElement());
    if (languageClass == null) return null;

    String anyLanguageId = calculateAnyLanguageId(context);
    return new LanguageDefinition(anyLanguageId, languageClass, AllIcons.FileTypes.Any_type, "<any language>");
  }

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
    if (CompletionContributorEP.class.getName().equals(epBeanClass.getStringValue())) {
      return "any";
    }

    return ANY_LANGUAGE_DEFAULT_ID;
  }

  static class LanguageDefinition {

    final String id;
    final PsiClass clazz;
    final Icon icon;
    final String displayName;

    LanguageDefinition(String id, PsiClass clazz, Icon icon, String displayName) {
      this.id = id;
      this.clazz = clazz;
      this.icon = icon;
      this.displayName = displayName;
    }
  }
}
