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
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

public abstract class MatchPredicate {
  /**
   * Matches given node against this predicate.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successful, false otherwise
   */
  public boolean match(PsiElement patternNode,PsiElement matchedNode, int start, int end, MatchContext context) {
    return match(patternNode,matchedNode,context);
  }

  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successful, false otherwise
   */
  public abstract boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context);
}
