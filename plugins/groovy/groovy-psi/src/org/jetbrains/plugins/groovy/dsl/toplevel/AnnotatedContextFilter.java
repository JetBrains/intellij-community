// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

import java.util.Collections;
import java.util.Map;

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
                                    ? ConcurrentFactoryMap.createMap(anno -> PsiSearchHelper.getInstance(file.getProject()).hasIdentifierInFile(file, anno))
                                    : Collections.emptyMap();
      return CachedValueProvider.Result.create(result, file);
    });
  }

  public static @Nullable PsiAnnotation findContextAnnotation(@NotNull PsiElement context, String annoQName) {
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

  private static @Nullable PsiAnnotation findAnnotation(PsiModifierList modifierList, String annoQName) {
    return modifierList != null ? modifierList.findAnnotation(annoQName) : null;
  }



}
