/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

/**
 * @author maxim
 */
public class MatchUtils {

  public static boolean compareWithNoDifferenceToPackage(String typeImage, String typeImage2) {
    return compareWithNoDifferenceToPackage(typeImage, typeImage2, false);
  }

  public static boolean compareWithNoDifferenceToPackage(String typeImage, @NonNls String typeImage2, boolean ignoreCase) {
    if (typeImage == null || typeImage2 == null) return typeImage == typeImage2;
    final boolean endsWith = ignoreCase ? StringUtil.endsWithIgnoreCase(typeImage2, typeImage) : typeImage2.endsWith(typeImage);
    return endsWith && (
      typeImage.length() == typeImage2.length() ||
      typeImage2.charAt(typeImage2.length()-typeImage.length() - 1)=='.' // package separator
    );
  }

  public static PsiElement getReferencedElement(PsiElement element) {
    if (element instanceof PsiReference) {
      return ((PsiReference)element).resolve();
    }

    /*if (element instanceof PsiTypeElement) {
      PsiType type = ((PsiTypeElement)element).getType();

      if (type instanceof PsiArrayType) {
        type = ((PsiArrayType)type).getComponentType();
      }
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
      return null;
    }*/
    return element;
  }
}
