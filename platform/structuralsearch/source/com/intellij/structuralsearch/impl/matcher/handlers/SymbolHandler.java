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
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Search handler for symbol search
 */
public class SymbolHandler extends MatchingHandler {
  private final SubstitutionHandler handler;

  public SymbolHandler(SubstitutionHandler handler) {
    this.handler = handler;
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return handler.canMatch(patternNode, matchedNode, context) && handler.handle(matchedNode, context);
  }
}
