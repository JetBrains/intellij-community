// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.structuralsearch.StructuralSearchProfile;

/**
 * @author Eugene.Kudelevsky
 */
public final class ReplacerUtil {
  private ReplacerUtil() {
  }

  public static PsiElement copySpacesAndCommentsBefore(PsiElement elementToReplace,
                                                       PsiElement[] patternElements,
                                                       String replacementToMake,
                                                       PsiElement elementParent) {
    int i = 0;
    while (true) {    // if it goes out of bounds then deep error happens
      if (!(patternElements[i] instanceof PsiComment || patternElements[i] instanceof PsiWhiteSpace)) {
        break;
      }
      ++i;
      if (patternElements.length == i) {
        break;
      }
    }

    if (patternElements.length == i) {
      Logger logger = Logger.getInstance(StructuralSearchProfile.class.getName());
      logger.error("Unexpected replacement structure:" + replacementToMake);
    }

    if (i != 0) {
      elementParent.addRangeBefore(patternElements[0], patternElements[i - 1], elementToReplace);
    }
    return patternElements[i];
  }
}
