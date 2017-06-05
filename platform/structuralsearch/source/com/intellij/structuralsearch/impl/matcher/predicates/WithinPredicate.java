/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class WithinPredicate extends MatchPredicate {
  private final MatchOptions myMatchOptions;
  private final Matcher matcher;

  public WithinPredicate(String within, FileType fileType, Project project) {
    if (StringUtil.isQuotedString(within)) {
      // keep old configurations working
      myMatchOptions = new MatchOptions();
      myMatchOptions.setLooseMatching(true);
      myMatchOptions.setFileType(fileType);
      myMatchOptions.fillSearchCriteria(StringUtil.unquoteString(within));
    }
    else {
      final Configuration configuration = ConfigurationManager.getInstance(project).findConfigurationByName(within);
      if (configuration == null) {
        throw new MalformedPatternException();
      }
      myMatchOptions = configuration.getMatchOptions();
    }

    matcher = new Matcher(project, myMatchOptions);
  }

  @Override
  public boolean match(PsiElement matchedNode, int start, int end, MatchContext context) {
    final List<MatchResult> results = matcher.matchByDownUp(matchedNode, myMatchOptions);
    for (MatchResult result : results) {
      if (PsiTreeUtil.isAncestor(result.getMatch(), matchedNode, false)) {
        return true;
      }
    }
    return false;
  }
}