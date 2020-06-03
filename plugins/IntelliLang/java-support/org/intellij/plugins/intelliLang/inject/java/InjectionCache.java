// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
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
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class InjectionCache {
  private final CachedValue<Set<String>> myAnnoIndex;
  private final CachedValue<Collection<String>> myXmlIndex;
  private final Project myProject;

  public InjectionCache(@NotNull Project project) {
    Configuration configuration = Configuration.getProjectInstance(project);
    myProject = project;
    myXmlIndex = CachedValuesManager.getManager(project).createCachedValue(() -> {
      final Map<ElementPattern<?>, BaseInjection> map = new THashMap<>();
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
      return new CachedValueProvider.Result<>(result, PsiModificationTracker.MODIFICATION_COUNT, configuration);
    }, false);
  }

  @NotNull
  private Set<String> collectMethodNamesWithLanguage(String annotationClassName) {
    GlobalSearchScope allScope = GlobalSearchScope.allScope(myProject);

    // todo use allScope once Kotlin support becomes fast enough (https://youtrack.jetbrains.com/issue/KT-13734)
    GlobalSearchScope usageScope = GlobalSearchScope.getScopeRestrictedByFileTypes(allScope, JavaFileType.INSTANCE);

    Set<String> result = new THashSet<>();
    ArrayList<PsiClass> annoClasses = ContainerUtil.newArrayList(JavaPsiFacade.getInstance(myProject).findClasses(annotationClassName, allScope));
    for (int cursor = 0; cursor < annoClasses.size(); cursor++) {
      Parameters parameters = new Parameters(annoClasses.get(cursor), usageScope, true, PsiClass.class, PsiParameter.class, PsiMethod.class);
      AnnotatedElementsSearch.searchElements(parameters).forEach(element -> {
        if (element instanceof PsiParameter) {
          final PsiElement scope = ((PsiParameter)element).getDeclarationScope();
          if (scope instanceof PsiMethod) {
            ContainerUtil.addIfNotNull(result, ((PsiMethod)scope).getName());
          }
        }
        else if (element instanceof PsiClass && ((PsiClass)element).isAnnotationType() && !annoClasses.contains(element)) {
          annoClasses.add((PsiClass)element);
        }
        else if (element instanceof PsiMethod) {
          ContainerUtil.addIfNotNull(result, ((PsiMember)element).getName());
        }
        return true;
      });
    }
    return result;
  }

  public static InjectionCache getInstance(Project project) {
    return ServiceManager.getService(project, InjectionCache.class);
  }

  public Set<String> getAnnoIndex() {
    return myAnnoIndex.getValue();
  }

  public Collection<String> getXmlIndex() {
    return myXmlIndex.getValue();
  }
}
