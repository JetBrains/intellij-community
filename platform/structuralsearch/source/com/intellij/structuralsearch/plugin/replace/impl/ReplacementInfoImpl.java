// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class ReplacementInfoImpl implements ReplacementInfo {
  private final @NotNull MatchResult matchResult;
  private final List<SmartPsiElementPointer<PsiElement>> matchesPtrList = new SmartList<>();
  private final Map<String, MatchResult> variableMap = new HashMap<>();
  private final Map<PsiElement, String> elementToVariableNameMap = new HashMap<>(1);
  private final Map<String, String> sourceNameToSearchPatternNameMap = new HashMap<>(1);

  private String replacement;

  ReplacementInfoImpl(@NotNull MatchResult matchResult, @NotNull Project project) {
    this.matchResult = matchResult;
    init(project);
  }

  private void init(@NotNull Project project) {
    fillPointerList(project);
    fillVariableMap(matchResult.getRoot());
    for(Map.Entry<String, MatchResult> entry : variableMap.entrySet()) {
      fillElementToVariableNameMap(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public String getReplacement() {
    return replacement;
  }

  public void setReplacement(@NotNull String replacement) {
    this.replacement = replacement;
  }

  @Override
  public @Nullable PsiElement getMatch(int index) {
    return matchesPtrList.get(index).getElement();
  }

  @Override
  public int getMatchesCount() {
    return matchesPtrList.size();
  }

  @Override
  public MatchResult getNamedMatchResult(@NotNull String name) {
    return variableMap.get(name);
  }

  @Override
  public @NotNull MatchResult getMatchResult() {
    return matchResult;
  }

  @Override
  public String getVariableName(@NotNull PsiElement element) {
    return elementToVariableNameMap.get(element);
  }

  @Override
  public String getSearchPatternName(@NotNull String sourceName) {
    return sourceNameToSearchPatternNameMap.get(sourceName);
  }

  private void fillPointerList(@NotNull Project project) {
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);

    if (MatchResult.MULTI_LINE_MATCH.equals(matchResult.getName())) {
      final Iterator<MatchResult> i = matchResult.getChildren().iterator();
      while (i.hasNext()) {
        final MatchResult r = i.next();

        if (MatchResult.LINE_MATCH.equals(r.getName())) {
          PsiElement element = r.getMatch();

          if (element instanceof PsiDocCommentBase) { // doc comment is not collapsed when created in block
            if (i.hasNext()) {
              final MatchResult son = i.next();

              if (!MatchResult.LINE_MATCH.equals(son.getName()) || !StructuralSearchUtil.isDocCommentOwner(son.getMatch())) {
                matchesPtrList.add(manager.createSmartPsiElementPointer(element));
              }
              element = son.getMatch();
            }
          }
          matchesPtrList.add(manager.createSmartPsiElementPointer(element));
        }
      }
    } else {
      matchesPtrList.add(manager.createSmartPsiElementPointer(matchResult.getMatch()));
    }
  }

  private void fillElementToVariableNameMap(final String name, final MatchResult matchResult) {
    final boolean multiMatch = matchResult.isMultipleMatch() || matchResult.isScopeMatch();
    if (matchResult.hasChildren() && multiMatch) {
      for (MatchResult r : matchResult.getChildren()) {
        fillElementToVariableNameMap(name, r);
      }
    } else if (!multiMatch) {
      final PsiElement match = matchResult.getMatch();
      if (match != null) {
        elementToVariableNameMap.put(match, name);
      }
    }
  }

  private void fillVariableMap(MatchResult r) {
    final String name = r.getName();
    if (name != null) {
      variableMap.putIfAbsent(name, r);
      sourceNameToSearchPatternNameMap.put(r.getMatchImage(), name);
    }

    if (!r.isScopeMatch() || !r.isMultipleMatch()) {
      for (final MatchResult matchResult : r.getChildren()) {
        fillVariableMap(matchResult);
      }
    } else if (r.hasChildren()) {
      fillVariableMap(r.getChildren().get(0));
    }
  }
}
