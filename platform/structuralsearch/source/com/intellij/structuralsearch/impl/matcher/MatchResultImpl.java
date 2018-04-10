// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import com.intellij.util.SmartList;

import java.util.Collections;
import java.util.List;

public final class MatchResultImpl extends MatchResult {
  private String name;
  private SmartPsiPointer matchRef;
  private int start;
  private int end = -1;
  private String matchImage;
  private final List<MatchResult> myChildren = new SmartList<>();
  private boolean target;

  private boolean myScopeMatch;
  private boolean myMultipleMatch;
  private MatchResultImpl parent = null;

  MatchResultImpl() {
  }

  public MatchResultImpl(String name, String image, SmartPsiPointer ref, boolean target) {
    this(name,image,ref,0,-1,target);
  }

  public MatchResultImpl(String name, String image, SmartPsiPointer ref, int start, int end, boolean target) {
    matchRef = ref;
    this.name = name;
    matchImage = image;
    this.target = target;
    this.start = start;
    this.end = end;
  }

  @Override
  public String getMatchImage() {
    return matchImage;
  }

  @Override
  public SmartPsiPointer getMatchRef() {
    return matchRef;
  }

  @Override
  public PsiElement getMatch() {
    if (matchRef == null) {
      return null;
    }
    return matchRef.getElement();
  }

  public void setMatchRef(SmartPsiPointer matchStart) {
    matchRef = matchStart;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<MatchResult> getChildren() {
    return Collections.unmodifiableList(myChildren);
  }

  @Override
  public int size() {
    if (!myMultipleMatch) return (getMatch() != null) ? 1 : 0;
    return myChildren.size();
  }

  @Override
  public boolean isScopeMatch() {
    return myScopeMatch;
  }

  @Override
  public boolean isMultipleMatch() {
    return myMultipleMatch;
  }

  public void clear() {
    if (matchRef != null) {
      matchRef.clear();
      matchRef = null;
    }

    for (final MatchResult match : myChildren) {
      ((MatchResultImpl)match).clear();
    }
    myChildren.clear();

    name = null;
    matchImage = null;
  }

  public boolean hasChildren() {
    return !myChildren.isEmpty();
  }

  public void removeChildren() {
    myChildren.clear();
  }

  public MatchResult removeLastChild() {
    return myChildren.remove(myChildren.size() - 1);
  }

  public void setScopeMatch(final boolean scopeMatch) {
    myScopeMatch = scopeMatch;
  }

  public void setMultipleMatch(final boolean multipleMatch) {
    myMultipleMatch = multipleMatch;
  }

  public MatchResultImpl findChild(String name) {
    // @todo this could be performance bottleneck, replace with hash lookup!
    for (final MatchResult match : myChildren) {
      final MatchResultImpl res = (MatchResultImpl)match;

      if (name.equals(res.getName())) {
        return res;
      }
    }
    return null;
  }

  public MatchResult removeChild(String typedVar) {
    // @todo this could be performance bottleneck, replace with hash lookup!
    for (int i = 0, size = myChildren.size(); i < size; i++) {
      final MatchResult child = myChildren.get(i);
      if (typedVar.equals(child.getName())) {
        myChildren.remove(i);
        return child;
      }
    }

    return null;
  }

  public void addChild(MatchResult result) {
    if (result instanceof MatchResultImpl) {
      ((MatchResultImpl)result).parent = this;
    }
    myChildren.add(result);
  }

  @Override
  public MatchResult getRoot() {
    if (parent == null) return this;
    MatchResultImpl root = parent;
    while (root.parent != null) {
      root = root.parent;
    }
    return root;
  }

  public void setMatchImage(String matchImage) {
    this.matchImage = matchImage;
  }

  @Override
  public boolean isTarget() {
    return target;
  }

  public void setTarget(boolean target) {
    this.target = target;
  }

  @Override
  public int getStart() {
    return start;
  }

  public void setStart(int start) {
    this.start = start;
  }

  @Override
  public int getEnd() {
    return end;
  }

  public void setEnd(int end) {
    this.end = end;
  }

  @Override
  public String toString() {
    return "MatchResultImpl{name='" + name + '\'' + ", matchImage='" + matchImage + '\'' + "}";
  }
}

