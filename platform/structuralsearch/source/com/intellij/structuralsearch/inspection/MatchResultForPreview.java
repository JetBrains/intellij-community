// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.MatchResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
class MatchResultForPreview extends MatchResult {
  private final MatchResult myDelegate;
  private final PsiFile myTarget;

  MatchResultForPreview(MatchResult delegate, PsiFile target) {
    myDelegate = delegate;
    myTarget = target;
  }

  @Override
  public String getMatchImage() {
    return myDelegate.getMatchImage();
  }

  @Override
  public SmartPsiElementPointer<?> getMatchRef() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getMatch() {
    final PsiElement match = myDelegate.getMatch();
    return PsiTreeUtil.findSameElementInCopy(match, myTarget);
  }

  @Override
  public int getStart() {
    return myDelegate.getStart();
  }

  @Override
  public int getEnd() {
    return myDelegate.getEnd();
  }

  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @Override
  public @NotNull List<MatchResult> getChildren() {
    final List<MatchResult> children = myDelegate.getChildren();
    if (children.isEmpty()) return children;
    final List<MatchResult> result = new ArrayList<>(children.size());
    for (MatchResult child : children) {
      result.add(new MatchResultForPreview(child, myTarget));
    }
    return result;
  }

  @Override
  public boolean hasChildren() {
    return myDelegate.hasChildren();
  }

  @Override
  public int size() {
    return myDelegate.size();
  }

  @Override
  public boolean isScopeMatch() {
    return myDelegate.isScopeMatch();
  }

  @Override
  public boolean isMultipleMatch() {
    return myDelegate.isMultipleMatch();
  }

  @Override
  public @NotNull MatchResult getRoot() {
    return new MatchResultForPreview(myDelegate.getRoot(), myTarget);
  }

  @Override
  public boolean isTarget() {
    return myDelegate.isTarget();
  }
}