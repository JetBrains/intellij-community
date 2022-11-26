// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to hold compiled pattern information. Contains the PSI pattern tree, and maps PsiElements to matching handlers.
 * @see MatchingHandler
 */
public abstract class CompiledPattern {
  public static final Key<MatchingHandler> HANDLER_KEY = Key.create("ss.handler");
  private final Map<Object, MatchingHandler> handlers = new HashMap<>();
  private final MultiMap<String, PsiElement> variableNodes = new MultiMap<>();
  private SearchScope scope;
  private NodeIterator nodes;
  private MatchingStrategy strategy;
  private PsiElement targetNode;
  private int nodeCount;
  private PsiElement last;
  private MatchingHandler lastHandler;

  public abstract String @NotNull [] getTypedVarPrefixes();
  public abstract boolean isTypedVar(@NotNull String str);

  public void setTargetNode(@NotNull PsiElement element) {
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

  public void setNodes(@NotNull List<? extends PsiElement> elements) {
    nodes = new ArrayBackedNodeIterator(PsiUtilCore.toPsiElementArray(elements));
    nodeCount = elements.size();
  }

  @Contract("null -> false")
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
  public String getTypedVarString(@NotNull PsiElement element) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    final String typedVarString = profile == null ? element.getText() : profile.getTypedVarString(element);
    return typedVarString.trim();
  }

  public MatchingHandler getHandlerSimple(@NotNull PsiElement node) {
    return handlers.get(node);
  }

  @NotNull
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

  public MatchingHandler getHandler(@NotNull String name) {
    return handlers.get(name);
  }

  public void setHandler(@NotNull PsiElement node, @NotNull MatchingHandler handler) {
    last = null;
    handlers.put(node, handler);
  }

  @NotNull
  public SubstitutionHandler createSubstitutionHandler(@NotNull String name, @NotNull String compiledName, boolean target, int minOccurs, int maxOccurs,
                                                       boolean greedy) {
    SubstitutionHandler handler = (SubstitutionHandler)handlers.get(compiledName);
    if (handler != null) return handler;

    handler = doCreateSubstitutionHandler(name, target, minOccurs, maxOccurs, greedy);
    handlers.put(compiledName, handler);
    return handler;
  }

  @NotNull
  protected SubstitutionHandler doCreateSubstitutionHandler(@NotNull String name, boolean target, int minOccurs, int maxOccurs, boolean greedy) {
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

  public boolean isToResetHandler(@NotNull PsiElement element) {
    return true;
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
