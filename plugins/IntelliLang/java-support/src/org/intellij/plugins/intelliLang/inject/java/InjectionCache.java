// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.AnnotatedElementsSearch.Parameters;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PatternValuesIndex;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Service(Service.Level.PROJECT)
public final class InjectionCache {
  private final CachedValue<Set<String>> myAnnoIndex;
  private final CachedValue<Collection<String>> myXmlIndex;
  private final Project myProject;

  public InjectionCache(@NotNull Project project) {
    Configuration configuration = Configuration.getProjectInstance(project);
    myProject = project;
    myXmlIndex = CachedValuesManager.getManager(project).createCachedValue(() -> {
      final Map<ElementPattern<?>, BaseInjection> map = new HashMap<>();
      for (BaseInjection injection : configuration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)) {
        for (InjectionPlace place : injection.getInjectionPlaces()) {
          if (!place.isEnabled() || place.getElementPattern() == null) continue;
          map.put(place.getElementPattern(), injection);
        }
      }
      final Set<String> stringSet = PatternValuesIndex.buildStringIndex(map.keySet());
      return new CachedValueProvider.Result<>(stringSet, configuration);
    }, false);

    myAnnoIndex = CachedValuesManager.getManager(project).createCachedValue(() -> {
      Set<String> result = collectMethodNamesWithLanguage(
        configuration.getAdvancedConfiguration().getLanguageAnnotationClass());
      ModificationTracker modificationTracker = PsiModificationTracker.getInstance(project).forLanguage(JavaLanguage.INSTANCE);
      return new CachedValueProvider.Result<>(result, modificationTracker, configuration);
    }, false);
  }

  private @NotNull Set<String> collectMethodNamesWithLanguage(String annotationClassName) {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(myProject);

    // todo use allScope once Kotlin support becomes fast enough (https://youtrack.jetbrains.com/issue/KT-13734)
    GlobalSearchScope usageScope = GlobalSearchScope.getScopeRestrictedByFileTypes(allScope, JavaFileType.INSTANCE);

    Set<String> result = new HashSet<>();
    List<PsiClass> annoClasses = new ArrayList<>(List.of(JavaPsiFacade.getInstance(myProject).findClasses(annotationClassName, allScope)));
    for (int cursor = 0; cursor < annoClasses.size(); cursor++) {
      Parameters parameters = new Parameters(annoClasses.get(cursor), usageScope, true,
                                             PsiClass.class, PsiParameter.class, PsiMethod.class, PsiRecordComponent.class);
      AnnotatedElementsSearch.searchElements(parameters).forEach(element -> {
        if (element instanceof PsiParameter psiParameter) {
          final PsiElement scope = psiParameter.getDeclarationScope();
          if (scope instanceof PsiMethod psiMethod) {
            ContainerUtil.addIfNotNull(result, psiMethod.getName());
          }
        }
        else if (element instanceof PsiClass psiClass && psiClass.isAnnotationType() && !annoClasses.contains(psiClass)) {
          annoClasses.add(psiClass);
        }
        else if (element instanceof PsiMethod psiMethod) {
          ContainerUtil.addIfNotNull(result, psiMethod.getName());
        }
        else if (element instanceof PsiRecordComponent psiRecordComponent) {
          final PsiClass psiClass = psiRecordComponent.getContainingClass();
          if (psiClass != null) {
            ContainerUtil.addIfNotNull(result, psiClass.getName());
          }
        }
        return true;
      });
    }
    return result;
  }

  public static InjectionCache getInstance(Project project) {
    return project.getService(InjectionCache.class);
  }

  public Set<String> getAnnoIndex() {
    return myAnnoIndex.getValue();
  }

  public Collection<String> getXmlIndex() {
    return myXmlIndex.getValue();
  }
}
