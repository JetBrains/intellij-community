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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrAnnotationUtil {
  @Nullable
  public static String inferStringAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    final PsiAnnotationMemberValue targetValue = annotation.findAttributeValue(attributeName);
    return getString(targetValue);
  }

  @Nullable
  public static String getString(@Nullable PsiAnnotationMemberValue targetValue) {
    if (targetValue instanceof PsiLiteral) {
      final Object value = ((PsiLiteral)targetValue).getValue();
      if (value instanceof String) return (String)value;
    }
    return null;
  }

  @Nullable
  public static Integer inferIntegerAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    final PsiAnnotationMemberValue targetValue = annotation.findAttributeValue(attributeName);
    if (targetValue instanceof PsiLiteral) {
      final Object value = ((PsiLiteral)targetValue).getValue();
      if (value instanceof Integer) return (Integer)value;
    }
    return null;
  }

  @Nullable
  public static Boolean inferBooleanAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    final PsiAnnotationMemberValue targetValue = annotation.findAttributeValue(attributeName);
    if (targetValue instanceof PsiLiteral) {
      final Object value = ((PsiLiteral)targetValue).getValue();
      if (value instanceof Boolean) return (Boolean)value;
    }
    return null;
  }

  public static boolean inferBooleanAttributeNotNull(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    Boolean result = inferBooleanAttribute(annotation, attributeName);
    return result != null && result;
  }

  @Nullable
  public static PsiClass inferClassAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    final PsiAnnotationMemberValue targetValue = annotation.findAttributeValue(attributeName);
    return getPsiClass(targetValue);
  }

  @Nullable
  private static PsiClass getPsiClass(@Nullable PsiAnnotationMemberValue targetValue) {
    if (targetValue instanceof PsiClassObjectAccessExpression) {
      PsiType type = ((PsiClassObjectAccessExpression)targetValue).getOperand().getType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
    }
    else if (targetValue instanceof GrReferenceExpression) {
      if ("class".equals(((GrReferenceExpression)targetValue).getReferenceName())) {
        GrExpression qualifier = ((GrReferenceExpression)targetValue).getQualifier();
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
          if (resolved instanceof PsiClass) {
            return (PsiClass)resolved;
          }
        }
      }
      PsiElement resolved = ((GrReferenceExpression)targetValue).resolve();
      if (resolved instanceof PsiClass) return (PsiClass)resolved;
    }
    return null;
  }

  @Nullable
  public static PsiType extractClassTypeFromClassAttributeValue(PsiAnnotationMemberValue targetValue) {
    if (targetValue instanceof PsiClassObjectAccessExpression) {
      return ((PsiClassObjectAccessExpression)targetValue).getOperand().getType();
    }
    else if (targetValue instanceof GrReferenceExpression) {
      if ("class".equals(((GrReferenceExpression)targetValue).getReferenceName())) {
        GrExpression qualifier = ((GrReferenceExpression)targetValue).getQualifier();
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
          if (resolved instanceof PsiClass) {
            return qualifier.getType();
          }
        }
      }
      PsiElement resolved = ((GrReferenceExpression)targetValue).resolve();
      if (resolved instanceof PsiClass) {
        return ((GrReferenceExpression)targetValue).getType();
      }
    }
    return null;
  }

  public static PsiElement getActualOwner(GrAnnotation annotation) {
    PsiAnnotationOwner owner = annotation.getOwner();
    if (owner instanceof PsiModifierList) return ((PsiModifierList)owner).getParent();

    return (PsiElement)owner;
  }

  public static List<PsiClass> getClassArrayValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
    if (value instanceof PsiArrayInitializerMemberValue) {
      return ContainerUtil.mapNotNull(((PsiArrayInitializerMemberValue)value).getInitializers(), GrAnnotationUtil::getPsiClass);
    }
    else if (value instanceof PsiReference) {
      PsiClass psiClass = getPsiClass(value);
      if (psiClass != null) return Collections.singletonList(psiClass);
    }

    return Collections.emptyList();
  }

  public static List<String> getStringArrayValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
    if (value instanceof PsiArrayInitializerMemberValue) {
      return ContainerUtil.mapNotNull(((PsiArrayInitializerMemberValue)value).getInitializers(), memberValue -> {
        String string = getString(memberValue);
        return StringUtil.isEmpty(string) ? null : string;
      });
    }
    else {
      String string = getString(value);
      if (!StringUtil.isEmpty(string)) {
        return Collections.singletonList(string);
      }
    }
    return Collections.emptyList();
  }
}
