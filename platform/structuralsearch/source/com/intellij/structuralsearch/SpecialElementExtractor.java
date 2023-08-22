// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

/**
 * @author Bas Leijdekkers
 */
public interface SpecialElementExtractor {
  ExtensionPointName<SpecialElementExtractor> EP_NAME = ExtensionPointName.create("com.intellij.structuralsearch.specialXmlTagExtractor");

  PsiElement[] extractSpecialElements(PsiElement element);
}