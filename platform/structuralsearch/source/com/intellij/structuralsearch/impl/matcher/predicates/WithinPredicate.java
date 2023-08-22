// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class WithinPredicate extends MatchPredicate {

  private final Matcher matcher;

  public WithinPredicate(@NotNull String within, @NotNull LanguageFileType fileType, @NotNull Project project) {
    matcher = Matcher.buildMatcher(project, fileType, within);
  }

  @Override
  public boolean match(@NotNull PsiElement matchedNode, int start, int end, @NotNull MatchContext context) {
    final List<MatchResult> results = matcher.matchByDownUp(matchedNode);
    for (MatchResult result : results) {
      if (PsiTreeUtil.isAncestor(result.getMatch(), matchedNode, false)) {
        return true;
      }
    }
    return false;
  }
}