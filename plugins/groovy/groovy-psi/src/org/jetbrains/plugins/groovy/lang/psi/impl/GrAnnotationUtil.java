// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtilKt.findDeclaredDetachedValue;
import static org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtilKt.getArrayValue;

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

  public static List<PsiClass> getClassArrayValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName, boolean declared) {
    PsiAnnotationMemberValue value =
      declared ? annotation.findDeclaredAttributeValue(attributeName) : annotation.findAttributeValue(attributeName);
    return ContainerUtil.mapNotNull(AnnotationUtil.arrayAttributeValues(value), GrAnnotationUtil::getPsiClass);
  }

  public static List<String> getStringArrayValue(@NotNull PsiAnnotation annotation, @NotNull String attributeName, boolean declared) {
    PsiAnnotationMemberValue value = findDetachedAttributeValue(annotation, attributeName, declared);
    if (value == null) return Collections.emptyList();
    return getArrayValue(value, AnnotationUtil::getStringAttributeValue);
  }

  @Nullable
  private static PsiAnnotationMemberValue findDetachedAttributeValue(@NotNull PsiAnnotation annotation,
                                                                     @Nullable String attributeName,
                                                                     boolean declared) {
    PsiAnnotationMemberValue declaredValue = findDeclaredDetachedValue(annotation, attributeName);
    if (declaredValue != null) return declaredValue;
    if (declared) return null;
    return annotation.findAttributeValue(attributeName);
  }
}
