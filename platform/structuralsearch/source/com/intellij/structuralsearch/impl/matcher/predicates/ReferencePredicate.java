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
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;

public final class ReferencePredicate extends MatchPredicate {

  private final String myName;

  public ReferencePredicate(String name) {
    myName = name;
  }

  @Override
  public boolean match(PsiElement node, PsiElement match, MatchContext context) {
    match = StructuralSearchUtil.getParentIfIdentifier(match);

    final PsiElement target = MatchUtils.getReferencedElement(match);
    if (target != null) {
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(target);
      assert profile != null;
      final String image = profile.getText(target, 0, -1);
      context.getResult().addSon(new MatchResultImpl(myName, image, new SmartPsiPointer(target), true));
    }
    return true;
  }
}
