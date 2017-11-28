/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.AbstractMatchingVisitor;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
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
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Visitor class to manage pattern matching
 */
@SuppressWarnings({"RefusedBequest"})
public class GlobalMatchingVisitor extends AbstractMatchingVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor");
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

  public void setResult(boolean result) {
    this.myResult = result;
  }

  public MatchContext getMatchContext() {
    return matchContext;
  }

  @Override
  protected boolean doMatchInAnyOrder(NodeIterator elements, NodeIterator elements2) {
    return matchContext.getPattern().getHandler(elements.current()).matchInAnyOrder(
      elements,
      elements2,
      matchContext
    );
  }

  @NotNull
  @Override
  protected NodeFilter getNodeFilter() {
    return LexicalNodesFilter.getInstance();
  }

  public final boolean handleTypedElement(final PsiElement typedElement, final PsiElement match) {
    MatchingHandler handler = matchContext.getPattern().getHandler(typedElement);
    final MatchingHandler initialHandler = handler;
    if (handler instanceof DelegatingHandler) {
      handler = ((DelegatingHandler)handler).getDelegate();
    }
    assert handler instanceof SubstitutionHandler :
      handler != null ? handler.getClass() : "null" + ' ' + (initialHandler != null ? initialHandler.getClass() : "null");

    return ((SubstitutionHandler)handler).handle(match, matchContext);
  }

  public boolean allowsAbsenceOfMatch(final PsiElement element) {
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
  public boolean match(final PsiElement el1, final PsiElement el2) {
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
   * @param nodes the pattern element for matching
   * @param nodes2 the tree element for matching
   * @return if they are equal and false otherwise
   */
  @Override
  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2) {
    if (!nodes.hasNext()) {
      return !nodes2.hasNext();
    }

    return matchContext.getPattern().getHandler(nodes.current()).matchSequentially(nodes, nodes2, matchContext);
  }

  /**
   * Descents the tree in depth finding matches
   *
   * @param elements the element for which the sons are looked for match
   */
  public void matchContext(final NodeIterator elements) {
    if (matchContext == null) {
      return;
    }
    final CompiledPattern pattern = matchContext.getPattern();
    final NodeIterator patternNodes = pattern.getNodes().clone();
    final MatchResultImpl saveResult = matchContext.hasResult() ? matchContext.getResult() : null;
    final List<PsiElement> saveMatchedNodes = matchContext.getMatchedNodes();

    try {
      matchContext.setResult(null);
      matchContext.setMatchedNodes(null);

      if (!patternNodes.hasNext()) return;
      final MatchingHandler firstMatchingHandler = pattern.getHandler(patternNodes.current());

      for (; elements.hasNext(); elements.advance()) {
        final PsiElement elementNode = elements.current();

        boolean matched = firstMatchingHandler.matchSequentially(patternNodes, elements, matchContext);

        if (matched) {
          MatchingHandler matchingHandler = matchContext.getPattern().getHandler(Configuration.CONTEXT_VAR_NAME);
          if (matchingHandler != null) {
            matched = ((SubstitutionHandler)matchingHandler).handle(elementNode, matchContext);
          }
        }

        final List<PsiElement> matchedNodes = matchContext.getMatchedNodes();

        if (matched && matchedNodes != null) {
          dispatchMatched(matchedNodes, matchContext.getResult());
        }

        matchContext.setMatchedNodes(null);
        matchContext.setResult(null);

        patternNodes.reset();
        if (matchedNodes != null && matchedNodes.size() > 0 && matched) {
          elements.rewind();
        }
      }
    }
    finally {
      matchContext.setResult(saveResult);
      matchContext.setMatchedNodes(saveMatchedNodes);
    }
  }

  private void dispatchMatched(final List<PsiElement> matchedNodes, MatchResultImpl result) {
    if (!matchContext.getOptions().isResultIsContextMatch() && doDispatch(result, result)) return;

    // There is no substitutions so show the context

    processNoSubstitutionMatch(matchedNodes, result);
    matchContext.getSink().newMatch(result);
  }

  private boolean doDispatch(final MatchResult result, MatchResultImpl context) {
    boolean ret = false;

    for (MatchResult r : result.getAllSons()) {
      if ((r.isScopeMatch() && !r.isTarget()) || r.isMultipleMatch()) {
        ret |= doDispatch(r, context);
      }
      else if (r.isTarget()) {
        ((MatchResultImpl)r).setContext(context);
        matchContext.getSink().newMatch(r);
        ret = true;
      }
    }
    return ret;
  }

  private static void processNoSubstitutionMatch(List<PsiElement> matchedNodes, MatchResultImpl result) {
    boolean complexMatch = matchedNodes.size() > 1;
    final PsiElement match = matchedNodes.get(0);

    if (!complexMatch) {
      result.setMatchRef(new SmartPsiPointer(match));
      result.setMatchImage(match.getText());
    }
    else {

      for (final PsiElement matchStatement : matchedNodes) {
        result.getMatches().add(new MatchResultImpl(
            MatchResult.LINE_MATCH,
            matchStatement.getText(),
            new SmartPsiPointer(matchStatement),
            true
          )
        );
      }

      result.setMatchRef(
        new SmartPsiPointer(match)
      );
      result.setMatchImage(
        match.getText()
      );
      result.setName(MatchResult.MULTI_LINE_MATCH);
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
    if (left == null) {
      return right == null;
    }
    else if (right == null) {
      return false;
    }
    final boolean caseSensitiveMatch = matchContext.getOptions().isCaseSensitiveMatch();
    final String leftText = left.getText();
    final String rightText = right.getText();
    return caseSensitiveMatch ? leftText.equals(rightText) : leftText.equalsIgnoreCase(rightText);
  }
}
