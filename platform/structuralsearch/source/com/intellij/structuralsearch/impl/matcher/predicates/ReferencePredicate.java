// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * @author Bas Leijdekkers
 */
public final class ReferencePredicate extends MatchPredicate {

  private final Matcher matcher;

  public ReferencePredicate(@NotNull String constraint, @NotNull LanguageFileType fileType, @NotNull Project project) {
    matcher = Matcher.buildMatcher(project, fileType, constraint);
  }

  @Override
  public boolean match(@NotNull PsiElement matchedNode, int start, int end, @NotNull MatchContext context) {
    matchedNode = StructuralSearchUtil.getParentIfIdentifier(matchedNode);
    final List<PsiReference> references = PsiReferenceService.getService().getReferences(matchedNode, PsiReferenceService.Hints.NO_HINTS);
    return references.stream().map(PsiReference::resolve).filter(Objects::nonNull).anyMatch(t -> {
      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(t);
      return profile != null && matcher.matchNode(profile.extendMatchedByDownUp(t));
    });
  }
}
