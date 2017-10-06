// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.PsiElement;

/**
 * @author Bas Leijdekkers
 */
public class SingleNodeIterator extends NodeIterator {

  private final PsiElement myNode;
  private boolean myHasNext = true;

  public SingleNodeIterator(PsiElement node) {
    myNode = node;
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
    myHasNext = true;
  }

  @Override
  public void reset() {
    myHasNext = true;
  }
}
