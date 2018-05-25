// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SimpleHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Class to hold compiled pattern information
 */
public abstract class CompiledPattern {
  public static final Key<Object> HANDLER_KEY = Key.create("ss.handler");
  private final Map<Object, MatchingHandler> handlers = new THashMap<>();
  private final MultiMap<String, PsiElement> variableNodes = MultiMap.createSmart();
  private SearchScope scope;
  private NodeIterator nodes;
  private MatchingStrategy strategy;
  private PsiElement targetNode;
  private int nodeCount;
  private PsiElement last;
  private MatchingHandler lastHandler;

  public abstract String[] getTypedVarPrefixes();
  public abstract boolean isTypedVar(String str);

  public void setTargetNode(final PsiElement element) {
    targetNode = element;
  }

  public PsiElement getTargetNode() {
    return targetNode;
  }

  public MatchingStrategy getStrategy() {
    return strategy;
  }

  public void setStrategy(MatchingStrategy strategy) {
    this.strategy = strategy;
  }

  public int getNodeCount() {
    return nodeCount;
  }

  public NodeIterator getNodes() {
    return nodes;
  }

  public void setNodes(List<PsiElement> elements) {
    this.nodes = new ArrayBackedNodeIterator(PsiUtilCore.toPsiElementArray(elements));
    this.nodeCount = elements.size();
  }

  public boolean isTypedVar(final PsiElement element) {
    return element != null && isTypedVar(element.getText());
  }

  public boolean isRealTypedVar(PsiElement element) {
    if (element == null || element.getTextLength() <= 0) {
      return false;
    }
    final String str = getTypedVarString(element);
    return !str.isEmpty() && isTypedVar(str);
  }

  @NotNull
  public String getTypedVarString(PsiElement element) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    if (profile == null) {
      return element.getText();
    }
    return profile.getTypedVarString(element);
  }

  public MatchingHandler getHandlerSimple(PsiElement node) {
    return handlers.get(node);
  }

  public MatchingHandler getHandler(@NotNull PsiElement node) {
    if (node == last) {
      return lastHandler;
    }
    MatchingHandler handler = handlers.get(node);

    if (handler == null) {
      handler = new SimpleHandler();
      setHandler(node, handler);
    }

    last = node;
    lastHandler = handler;

    return handler;
  }

  public MatchingHandler getHandler(String name) {
    return handlers.get(name);
  }

  public void setHandler(PsiElement node, MatchingHandler handler) {
    last = null;
    handlers.put(node, handler);
  }

  public SubstitutionHandler createSubstitutionHandler(String name, String compiledName, boolean target, int minOccurs, int maxOccurs,
                                                       boolean greedy) {
    SubstitutionHandler handler = (SubstitutionHandler)handlers.get(compiledName);
    if (handler != null) return handler;

    handler = doCreateSubstitutionHandler(name, target, minOccurs, maxOccurs, greedy);
    handlers.put(compiledName, handler);
    return handler;
  }

  protected SubstitutionHandler doCreateSubstitutionHandler(String name, boolean target, int minOccurs, int maxOccurs, boolean greedy) {
    return new SubstitutionHandler(name, target, minOccurs, maxOccurs, greedy);
  }

  public SearchScope getScope() {
    return scope;
  }

  public void setScope(SearchScope scope) {
    this.scope = scope;
  }

  public void clearHandlers() {
    handlers.clear();
    last = null;
    lastHandler = null;
  }

  void clearHandlersState() {
    for (final MatchingHandler h : handlers.values()) {
      if (h != null) h.reset();
    }
  }

  public boolean isToResetHandler(PsiElement element) {
    return true;
  }

  @Nullable
  public String getAlternativeTextToMatch(PsiElement node, String previousText) {
    return null;
  }

  @NotNull
  public List<PsiElement> getVariableNodes(@NotNull String name) {
    final Collection<PsiElement> elements = variableNodes.get(name);
    return elements instanceof List ? (List<PsiElement>)elements : new SmartList<>(elements);
  }

  public void putVariableNode(@NotNull String name, @NotNull PsiElement node) {
    variableNodes.putValue(name, node);
  }
}
