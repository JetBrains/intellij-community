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
package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;

public class XmlMatchingStrategy implements MatchingStrategy {

  private static final MatchingStrategy INSTANCE = new XmlMatchingStrategy();

  private XmlMatchingStrategy() {}

  @Override
  public boolean continueMatching(final PsiElement start) {
    return start instanceof XmlTag;
  }

  @Override
  public boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    return false;
  }

  public static MatchingStrategy getInstance() {
    return INSTANCE;
  }
}
