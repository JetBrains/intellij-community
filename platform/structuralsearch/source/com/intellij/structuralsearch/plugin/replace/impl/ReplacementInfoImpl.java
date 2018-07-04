// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class ReplacementInfoImpl implements ReplacementInfo {
  private final MatchResult matchResult;
  private final List<SmartPsiElementPointer> matchesPtrList = new SmartList<>();
  private final Map<String, MatchResult> variableMap = new HashMap<>();
  private final Map<PsiElement, String> elementToVariableNameMap = new HashMap<>(1);
  private final Map<String, String> sourceNameToSearchPatternNameMap = new HashMap<>(1);

  private String replacement;

  ReplacementInfoImpl(MatchResult matchResult, Project project) {
    this.matchResult = matchResult;
    init(project);
  }

  private void init(Project project) {
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

  @Override
  public void setReplacement(String replacement) {
    this.replacement = replacement;
  }

  @Nullable
  @Override
  public PsiElement getMatch(int index) {
    return matchesPtrList.get(index).getElement();
  }

  @Override
  public int getMatchesCount() {
    return matchesPtrList.size();
  }

  @Override
  public MatchResult getNamedMatchResult(String name) {
    return variableMap.get(name);
  }

  @Override
  public MatchResult getMatchResult() {
    return matchResult;
  }

  @Override
  public String getVariableName(PsiElement element) {
    return elementToVariableNameMap.get(element);
  }

  @Override
  public String getSearchPatternName(String sourceName) {
    return sourceNameToSearchPatternNameMap.get(sourceName);
  }

  private void fillPointerList(Project project) {
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

              if (MatchResult.LINE_MATCH.equals(son.getName()) && StructuralSearchUtil.isDocCommentOwner(son.getMatch())) {
                element = son.getMatch();
              } else {
                matchesPtrList.add(manager.createSmartPsiElementPointer(element));
                element = son.getMatch();
              }
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
    } else if (!multiMatch && matchResult.getMatchRef() != null)  {
      elementToVariableNameMap.put(matchResult.getMatch(), name);
    }
  }

  private void fillVariableMap(MatchResult r) {
    final String name = r.getName();
    if (name != null) {
      variableMap.putIfAbsent(name, r);

      final PsiElement element = StructuralSearchUtil.getParentIfIdentifier(r.getMatch());
      if (element instanceof PsiNamedElement) {
        sourceNameToSearchPatternNameMap.put(((PsiNamedElement)element).getName(), name);
      }
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
