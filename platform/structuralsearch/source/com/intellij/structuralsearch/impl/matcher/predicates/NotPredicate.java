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
import org.jetbrains.annotations.NotNull;

/**
 * Negates predicate
 */
public final class NotPredicate extends MatchPredicate {
  private final MatchPredicate myPredicate;

  public NotPredicate(@NotNull MatchPredicate predicate) {
    myPredicate = predicate;
  }

  @Override
  public boolean match(@NotNull PsiElement matchedNode, int start, int end, @NotNull MatchContext context) {
    return !myPredicate.match(matchedNode, start, end, context);
  }

  public MatchPredicate getPredicate() {
    return myPredicate;
  }
}
