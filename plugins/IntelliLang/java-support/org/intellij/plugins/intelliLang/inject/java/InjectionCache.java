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
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by Max Medvedev on 22/03/14
 */
public class InjectionCache {
  private final CachedValue<Collection<String>> myAnnoIndex;
  private final CachedValue<Collection<String>> myXmlIndex;

  public InjectionCache(final Project project, final Configuration configuration) {

    myXmlIndex = CachedValuesManager.getManager(project).createCachedValue(() -> {
      final Map<ElementPattern<?>, BaseInjection> map = new THashMap<>();
      for (BaseInjection injection : configuration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)) {
        for (InjectionPlace place : injection.getInjectionPlaces()) {
          if (!place.isEnabled() || place.getElementPattern() == null) continue;
          map.put(place.getElementPattern(), injection);
        }
      }
      final Set<String> stringSet = PatternValuesIndex.buildStringIndex(map.keySet());
      return new CachedValueProvider.Result<Collection<String>>(stringSet, configuration);
    }, false);

    myAnnoIndex = CachedValuesManager.getManager(project).createCachedValue(() -> {
      final String annotationClass = configuration.getAdvancedConfiguration().getLanguageAnnotationClass();
      final Collection<String> result = new THashSet<>();
      final ArrayList<String> annoClasses = new ArrayList<>(3);
      annoClasses.add(StringUtil.getShortName(annotationClass));
      for (int cursor = 0; cursor < annoClasses.size(); cursor++) {
        final String annoClass = annoClasses.get(cursor);
        for (PsiAnnotation annotation : JavaAnnotationIndex.getInstance().get(annoClass, project, GlobalSearchScope.allScope(project))) {
          final PsiElement modList = annotation.getParent();
          if (!(modList instanceof PsiModifierList)) continue;
          final PsiElement element = modList.getParent();
          if (element instanceof PsiParameter) {
            final PsiElement scope = ((PsiParameter)element).getDeclarationScope();
            if (scope instanceof PsiNamedElement) {
              ContainerUtil.addIfNotNull(result, ((PsiNamedElement)scope).getName());
            }
            else {
              ContainerUtil.addIfNotNull(result, ((PsiNamedElement)element).getName());
            }
          }
          else if (element instanceof PsiNamedElement) {
            if (element instanceof PsiClass && ((PsiClass)element).isAnnotationType()) {
              final String s = ((PsiClass)element).getName();
              if (!annoClasses.contains(s)) annoClasses.add(s);
            }
            else {
              ContainerUtil.addIfNotNull(result, ((PsiNamedElement)element).getName());
            }
          }
        }
      }
      return new CachedValueProvider.Result<>(result, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, configuration);
    }, false);
  }

  public static InjectionCache getInstance(Project project) {
    return ServiceManager.getService(project, InjectionCache.class);
  }

  public Collection<String> getAnnoIndex() {
    return myAnnoIndex.getValue();
  }

  public Collection<String> getXmlIndex() {
    return myXmlIndex.getValue();
  }
}
