// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

/**
 * A reference which can be affected by a "Change Signature" refactoring.
 */
public interface PsiCallReference extends PsiReference {
  PsiElement handleChangeSignature(ChangeInfo changeInfo);
}
