package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class describing the match result
 */
public final class MatchResultImpl extends MatchResult {
  private String name;
  private SmartPsiPointer matchRef;
  private int start;
  private int end = -1;
  private String matchImage;
  private List<MatchResult> matches;
  private boolean target;

  private boolean myScopeMatch;
  private boolean myMultipleMatch;
  private MatchResultImpl myContext;

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

  public List<MatchResult> getMatches() {
    if (matches==null) matches = new ArrayList<>();
    return matches;
  }

  @Override
  public List<MatchResult> getAllSons() {
    return getMatches();
  }

  @Override
  public boolean hasSons() {
    return matches!=null && matches.size() > 0;
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

    if (matches != null) {
      for (final MatchResult match : matches) {
        ((MatchResultImpl)match).clear();
      }
      matches = null;
    }

    name = null;
    matchImage = null;
  }

  public void clearMatches() {
    matches = null;
  }

  public void setScopeMatch(final boolean scopeMatch) {
    myScopeMatch = scopeMatch;
  }

  public void setMultipleMatch(final boolean multipleMatch) {
    myMultipleMatch = multipleMatch;
  }

  public MatchResultImpl findSon(String name) {
    if (matches!=null) {
      // @todo this could be performance bottleneck, replace with hash lookup!
      for (final MatchResult match : matches) {
        final MatchResultImpl res = (MatchResultImpl)match;

        if (name.equals(res.getName())) {
          return res;
        }
      }
    }
    return null;
  }

  public MatchResultImpl removeSon(String typedVar) {
    if (matches == null) return null;

    // @todo this could be performance bottleneck, replace with hash lookup!
    for(Iterator<MatchResult> i=matches.iterator();i.hasNext();) {
      final MatchResultImpl res = (MatchResultImpl)i.next();
      if (typedVar.equals(res.getName())) {
        i.remove();
        return res;
      }
    }

    return null;
  }

  public void addSon(MatchResultImpl result) {
    getMatches().add(result);
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

  public boolean isMatchImageNull() {
    return matchImage==null;
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

  public void setContext(final MatchResultImpl context) {
    myContext = context;
  }

  public MatchResultImpl getContext() {
    return myContext;
  }

  @Override
  public String toString() {
    return "MatchResultImpl{name='" + name + '\'' + ", matchImage='" + matchImage + '\'' + "}";
  }
}

