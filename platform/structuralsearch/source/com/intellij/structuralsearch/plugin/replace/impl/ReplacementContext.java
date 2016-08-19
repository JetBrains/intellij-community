package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 27.09.2005
 * Time: 14:27:20
 * To change this template use File | Settings | File Templates.
 */
public class ReplacementContext {
  ReplacementInfoImpl replacementInfo;
  ReplaceOptions options;
  Project project;

  public ReplaceOptions getOptions() {
    return options;
  }

  public Project getProject() {
    return project;
  }

  ReplacementContext(ReplaceOptions _options, Project _project) {
    options = _options;
    project = _project;
  }

  public Map<String, String> getNewName2PatternNameMap() {
    Map<String, String> newNameToSearchPatternNameMap = new HashMap<>(1);
    final Map<String, MatchResult> variableMap = replacementInfo.getVariableMap();

    if (variableMap != null) {
      for (String s : variableMap.keySet()) {
        final MatchResult matchResult = replacementInfo.getVariableMap().get(s);
        PsiElement match = matchResult.getMatch();
        match = StructuralSearchUtil.getParentIfIdentifier(match);

        if (match instanceof PsiNamedElement) {
          final String name = ((PsiNamedElement)match).getName();

          newNameToSearchPatternNameMap.put(name, s);
        }
      }
    }
    return newNameToSearchPatternNameMap;
  }
}
