package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;

/**
 * Filter for lexical nodes
 */
public final class LexicalNodesFilter implements NodeFilter {

  private final ThreadLocal<Boolean> careKeyWords = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };
  private final ThreadLocal<Boolean> result = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  private LexicalNodesFilter() {}

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  public boolean getResult() {
    return result.get().booleanValue();
  }

  public void setResult(boolean result) {
    this.result.set(Boolean.valueOf(result));
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new LexicalNodesFilter();
  }

  public boolean isCareKeyWords() {
    return careKeyWords.get().booleanValue();
  }

  public void setCareKeyWords(boolean careKeyWords) {
    this.careKeyWords.set(Boolean.valueOf(careKeyWords));
  }

  public boolean accepts(PsiElement element) {
    result.set(Boolean.FALSE);
    if (element!=null) {
      final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
      if (profile != null) {
        element.accept(profile.getLexicalNodesFilter(this));
      }
    }
    return result.get().booleanValue();
  }
}
