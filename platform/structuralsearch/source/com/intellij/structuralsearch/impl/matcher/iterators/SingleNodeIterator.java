// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SingleNodeIterator extends NodeIterator {

  public static final SingleNodeIterator EMPTY = new SingleNodeIterator(null);

  private final PsiElement myNode;
  private boolean myHasNext;

  private SingleNodeIterator(PsiElement node) {
    myNode = node;
    myHasNext = node != null;
  }

  @NotNull
  public static SingleNodeIterator newSingleNodeIterator(@Nullable PsiElement node) {
    return node == null ? EMPTY : new SingleNodeIterator(node);
  }

  @Override
  public boolean hasNext() {
    return myHasNext;
  }

  @Override
  public PsiElement current() {
    return myHasNext ? myNode : null;
  }

  @Override
  public void advance() {
    myHasNext = false;
  }

  @Override
  public void rewind() {
    reset();
  }

  @Override
  public void reset() {
    myHasNext = myNode != null;
  }
}
