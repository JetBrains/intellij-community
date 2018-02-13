// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.getStringAttributeValue;
import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;

/**
 * @author Max Medvedev
 */
public class GrAnnotationUtil {

  @Nullable
  public static String inferStringAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    return getStringAttributeValue(annotation, attributeName);
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

  @NotNull
  public static List<PsiClass> getClassArrayValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName, boolean declared) {
    PsiAnnotationMemberValue value =
      declared ? annotation.findDeclaredAttributeValue(attributeName) : annotation.findAttributeValue(attributeName);
    if (value instanceof PsiArrayInitializerMemberValue) {
      return mapNotNull(((PsiArrayInitializerMemberValue)value).getInitializers(), GrAnnotationUtil::getPsiClass);
    }
    else if (value instanceof PsiReference) {
      PsiClass psiClass = getPsiClass(value);
      if (psiClass != null) return Collections.singletonList(psiClass);
    }

    return Collections.emptyList();
  }

  @NotNull
  public static List<String> getStringArrayValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName, boolean declared) {
    PsiAnnotationMemberValue value = declared ? annotation.findDeclaredAttributeValue(attributeName)
                                              : annotation.findAttributeValue(attributeName);
    if (value instanceof PsiArrayInitializerMemberValue) {
      PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
      return mapNotNull(initializers, it -> nullize(getStringAttributeValue(it), true));
    }
    else {
      String string = value == null ? null : nullize(getStringAttributeValue(value), true);
      return string == null ? emptyList() : Collections.singletonList(string);
    }
  }
}
