// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.s
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

public final class ReferencePredicate extends MatchPredicate {

  private final Matcher matcher;

  public ReferencePredicate(String constraint, FileType fileType, Project project) {
    matcher = Matcher.buildMatcher(project, fileType, constraint);
  }

  @Override
  public boolean match(PsiElement matchedNode, int start, int end, MatchContext context) {
    matchedNode = StructuralSearchUtil.getParentIfIdentifier(matchedNode);
    if (!(matchedNode instanceof PsiReference)) {
      return false;
    }
    final PsiElement target = ((PsiReference)matchedNode).resolve();
    return target != null && matcher.matchNode(target);
  }
}
