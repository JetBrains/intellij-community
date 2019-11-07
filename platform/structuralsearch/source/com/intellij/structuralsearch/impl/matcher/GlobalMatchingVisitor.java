// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.AbstractMatchingVisitor;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.DelegatingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.structuralsearch.impl.matcher.iterators.SingleNodeIterator.newSingleNodeIterator;

/**
 * Visitor class to manage pattern matching
 */
public class GlobalMatchingVisitor extends AbstractMatchingVisitor {
  private static final Logger LOG = Logger.getInstance(GlobalMatchingVisitor.class);
  public static final Key<List<? extends PsiElement>> UNMATCHED_ELEMENTS_KEY = Key.create("UnmatchedElements");

  // the pattern element for visitor check
  private PsiElement myElement;

  // the result of matching in visitor
  private boolean myResult;

  // context of matching
  private MatchContext matchContext;

  private final Map<Language, PsiElementVisitor> myLanguage2MatchingVisitor = new HashMap<>(1);

  public PsiElement getElement() {
    return myElement;
  }

  public boolean getResult() {
    return myResult;
  }

  @Contract("true->true;false->false")
  public boolean setResult(boolean result) {
    return this.myResult = result;
  }

  public MatchContext getMatchContext() {
    return matchContext;
  }

  @Override
  protected boolean doMatchInAnyOrder(@NotNull NodeIterator elements, @NotNull NodeIterator elements2) {
    return matchContext.getPattern().getHandler(elements.current()).matchInAnyOrder(
      elements,
      elements2,
      matchContext
    );
  }

  @Override
  public boolean matchOptionally(@Nullable PsiElement patternNode, @Nullable PsiElement matchNode) {
    return patternNode == null && isLeftLooseMatching() ||
           matchSequentially(newSingleNodeIterator(patternNode), newSingleNodeIterator(matchNode));
  }

  @NotNull
  @Override
  protected NodeFilter getNodeFilter() {
    return LexicalNodesFilter.getInstance();
  }

  public final boolean handleTypedElement(PsiElement typedElement, PsiElement match) {
    final MatchingHandler initialHandler = matchContext.getPattern().getHandler(typedElement);
    MatchingHandler handler = initialHandler;
    if (handler instanceof DelegatingHandler) {
      handler = ((DelegatingHandler)handler).getDelegate();
    }
    assert handler instanceof SubstitutionHandler : typedElement + " has handler " +
                                                    (handler != null ? handler.getClass() : "null" + ' ' + initialHandler.getClass());

    return ((SubstitutionHandler)handler).handle(match, matchContext);
  }

  public boolean allowsAbsenceOfMatch(PsiElement element) {
    final MatchingHandler handler = getMatchContext().getPattern().getHandler(element);
    return handler instanceof SubstitutionHandler && ((SubstitutionHandler)handler).getMinOccurs() == 0;
  }

  /**
   * Identifies the match between given element of program tree and pattern element
   *
   * @param el1 the pattern for matching
   * @param el2 the tree element for matching
   * @return true if equal and false otherwise
   */
  @Override
  public boolean match(PsiElement el1, PsiElement el2) {
    if (el1 == el2) return true;
    if (el1 == null) {
      // absence of pattern element is match
      return true;
    }
    if (el2 == null) {
      // absence of match element needs check if allowed.
      return allowsAbsenceOfMatch(el1);
    }

    // copy changed data to local stack
    PsiElement prevElement = myElement;
    myElement = el2;

    try {
      PsiElementVisitor visitor = getVisitorForElement(el1);
      if (visitor != null) {
        el1.accept(visitor);
      }
    }
    catch (ClassCastException ex) {
      myResult = false;
    }
    finally {
      myElement = prevElement;
    }

    return myResult;
  }

  @Nullable
  private PsiElementVisitor getVisitorForElement(PsiElement element) {
    Language language = element.getLanguage();
    PsiElementVisitor visitor = myLanguage2MatchingVisitor.get(language);
    if (visitor == null) {
      visitor = createMatchingVisitor(language);
      myLanguage2MatchingVisitor.put(language, visitor);
    }
    return visitor;
  }

  @Nullable
  private PsiElementVisitor createMatchingVisitor(Language language) {
    StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
    if (profile == null) {
      LOG.warn("there is no StructuralSearchProfile for language " + language.getID());
      return null;
    }
    else {
      return profile.createMatchingVisitor(this);
    }
  }

  /**
   * Matches tree segments starting with given elements to find equality
   *
   * @param patternNodes the pattern element for matching
   * @param matchNodes the tree element for matching
   * @return if they are equal and false otherwise
   */
  @Override
  public boolean matchSequentially(@NotNull NodeIterator patternNodes, @NotNull NodeIterator matchNodes) {
    if (!patternNodes.hasNext()) {
      while (matchNodes.current() instanceof PsiComment) matchNodes.advance();
      return !matchNodes.hasNext();
    }
    final PsiElement current = patternNodes.current();
    return matchContext.getPattern().getHandler(current).matchSequentially(patternNodes, matchNodes, matchContext);
  }

  /**
   * Descents the tree in depth finding matches
   *
   * @param elements the element for which the sons are looked for match
   */
  public void matchContext(@NotNull NodeIterator elements) {
    final CompiledPattern pattern = matchContext.getPattern();
    final NodeIterator patternNodes = pattern.getNodes().clone();
    final MatchResultImpl saveResult = matchContext.hasResult() ? matchContext.getResult() : null;
    matchContext.saveMatchedNodes();

    try {
      if (!patternNodes.hasNext()) return;
      final MatchingHandler firstMatchingHandler = pattern.getHandler(patternNodes.current());

      for (; elements.hasNext(); elements.advance()) {
        matchContext.setResult(null);
        matchContext.clearMatchedNodes();
        final PsiElement elementNode = elements.current();

        final boolean patternMatched = firstMatchingHandler.matchSequentially(patternNodes, elements, matchContext);
        final boolean contextMatched;
        if (patternMatched) {
          final MatchingHandler matchingHandler = pattern.getHandler(Configuration.CONTEXT_VAR_NAME);
          contextMatched = matchingHandler == null || ((SubstitutionHandler)matchingHandler).handle(elementNode, matchContext);
        }
        else {
          contextMatched = false;
        }

        if (contextMatched) matchContext.dispatchMatched();

        patternNodes.reset();
        if (patternMatched) {
          elements.rewind();
        }
      }
    }
    finally {
      matchContext.setResult(saveResult);
      matchContext.restoreMatchedNodes();
    }
  }

  public void setMatchContext(MatchContext matchContext) {
    this.matchContext = matchContext;
  }

  @Override
  public boolean isLeftLooseMatching() {
    return matchContext.getOptions().isLooseMatching();
  }

  @Override
  public boolean isRightLooseMatching() {
    return false;
  }

  public boolean matchText(@Nullable PsiElement left, @Nullable PsiElement right) {
    if (left == null) return right == null;
    return right != null && matchText(left.getText(), right.getText());
  }

  public boolean matchText(String left, String right) {
    return matchContext.getOptions().isCaseSensitiveMatch() ? left.equals(right) : left.equalsIgnoreCase(right);
  }

  public void scopeMatch(PsiElement patternNode, boolean typedVar, PsiElement matchNode) {
    final MatchResultImpl ourResult = matchContext.hasResult() ? matchContext.getResult() : null;
    matchContext.popResult();

    if (myResult) {
      if (typedVar) {
        final SubstitutionHandler handler = (SubstitutionHandler)matchContext.getPattern().getHandler(patternNode);
        if (ourResult != null) ourResult.setScopeMatch(true);
        handler.setNestedResult(ourResult);
        setResult(handler.handle(matchNode, matchContext));

        final MatchResultImpl nestedResult = handler.getNestedResult();
        if (nestedResult != null) { // some constraint prevent from adding
          copyResults(nestedResult);
          handler.setNestedResult(null);
        }
      }
      else if (ourResult != null) {
        copyResults(ourResult);
      }
    }
  }

  private void copyResults(MatchResult ourResult) {
    final MatchResultImpl result = matchContext.getResult();
    for (MatchResult son : ourResult.getChildren()) result.addChild(son);
  }
}
