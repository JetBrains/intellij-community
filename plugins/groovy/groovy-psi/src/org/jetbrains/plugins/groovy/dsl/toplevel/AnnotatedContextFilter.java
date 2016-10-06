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
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

import java.util.Collections;
import java.util.Map;

/**
 * @author peter
 */
public class AnnotatedContextFilter implements ContextFilter {

  private final String myAnnoQName;

  public AnnotatedContextFilter(String annoQName) {
    myAnnoQName = annoQName;
  }

  @Override
  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    if (getPossibleAnnotations(descriptor.getPlaceFile()).get(StringUtil.getShortName(myAnnoQName)) != Boolean.TRUE) {
      return false;
    }

    return findContextAnnotation(descriptor.getPlace(), myAnnoQName) != null;
  }

  private static Map<String, Boolean> getPossibleAnnotations(final PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      Map<String, Boolean> result = StringUtil.contains(file.getViewProvider().getContents(), "@")
                                    ? ConcurrentFactoryMap.createConcurrentMap(anno -> containsString(anno, file))
                                    : Collections.emptyMap();
      return CachedValueProvider.Result.create(result, file);
    });
  }

  @NotNull
  private static Boolean containsString(String anno, PsiFile file) {
    if (file.getVirtualFile() == null || DumbService.isDumb(file.getProject())) {
      return StringUtil.contains(file.getViewProvider().getContents(), anno);
    }

    GlobalSearchScope scope = GlobalSearchScope.fileScope(file);
    return !FileBasedIndex.getInstance().getContainingFiles(IdIndex.NAME, new IdIndexEntry(anno, true), scope).isEmpty();
  }

  @Nullable public static PsiAnnotation findContextAnnotation(@NotNull PsiElement context, String annoQName) {
    PsiElement current = context;
    while (current != null) {
      if (current instanceof PsiModifierListOwner) {
        if (!(current instanceof GrVariableDeclaration)) {
          PsiAnnotation annotation = findAnnotation(((PsiModifierListOwner)current).getModifierList(), annoQName);
          if (annotation != null) {
            return annotation;
          }
        }
      }
      else if (current instanceof PsiFile) {
        if (current instanceof GroovyFile) {
          final GrPackageDefinition packageDefinition = ((GroovyFile)current).getPackageDefinition();
          if (packageDefinition != null) {
            return findAnnotation(packageDefinition.getAnnotationList(), annoQName);
          }
        }
        return null;
      }

      current = current.getContext();
    }

    return null;
  }

  @Nullable private static PsiAnnotation findAnnotation(PsiModifierList modifierList, String annoQName) {
    return modifierList != null ? modifierList.findAnnotation(annoQName) : null;
  }



}
