/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
public class GrAnnotationUtil {
  @Nullable
  public static String inferStringAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    final PsiAnnotationMemberValue targetValue = annotation.findAttributeValue(attributeName);
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
}
