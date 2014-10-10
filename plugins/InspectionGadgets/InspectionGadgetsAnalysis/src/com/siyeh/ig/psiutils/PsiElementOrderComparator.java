/*
 * Copyright 2007-2014 Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiElement;

import java.util.Comparator;

public class PsiElementOrderComparator implements Comparator<PsiElement> {

  private static final PsiElementOrderComparator INSTANCE = new PsiElementOrderComparator();

  private PsiElementOrderComparator() {}

  @Override
  public int compare(PsiElement element1, PsiElement element2) {
    final int offset1 = element1.getTextOffset();
    final int offset2 = element2.getTextOffset();
    return offset1 - offset2;
  }

  public static PsiElementOrderComparator getInstance() {
    return INSTANCE;
  }
}