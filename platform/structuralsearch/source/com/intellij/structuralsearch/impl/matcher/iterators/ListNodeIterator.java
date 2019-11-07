// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ListNodeIterator extends NodeIterator {

  private final List<PsiElement> myList;
  private int index = 0;

  public ListNodeIterator(List<PsiElement> list) {
    myList = list;
  }

  @Override
  public boolean hasNext() {
    return index < myList.size();
  }

  @Override
  public PsiElement current() {
    if (index >= myList.size()) return null;
    return myList.get(index);
  }

  @Override
  public void advance() {
    index++;
  }

  @Override
  public void rewind() {
    index--;
  }

  @Override
  public void reset() {
    index = 0;
  }
}
