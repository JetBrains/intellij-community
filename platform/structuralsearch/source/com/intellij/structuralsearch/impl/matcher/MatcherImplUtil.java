// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.PatternContext;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;

/**
 * @author Maxim.Mossienko
 */
public class MatcherImplUtil {

  public static PsiElement[] createTreeFromText(String text, PatternTreeContext context, LanguageFileType fileType, Project project) {
    return createTreeFromText(text, context, fileType, null, null, project, false);
  }

  public static PsiElement[] createSourceTreeFromText(String text,
                                                      PatternTreeContext context,
                                                      LanguageFileType fileType,
                                                      Project project,
                                                      boolean physical) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(fileType.getLanguage());
    if (profile != null) {
      return profile.createPatternTree(text, context, fileType, fileType.getLanguage(), null, project, physical);
    }
    return PsiElement.EMPTY_ARRAY;
  }

  public static PsiElement[] createTreeFromText(String text,
                                                PatternTreeContext context,
                                                LanguageFileType fileType,
                                                Language language,
                                                PatternContext patternContext,
                                                Project project,
                                                boolean physical) {
    if (language == null) {
      language = fileType.getLanguage();
    }
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
    if (profile != null) {
      final String contextId = (patternContext == null) ? null : patternContext.getId();
      return profile.createPatternTree(text, context, fileType, language, contextId, project, physical);
    }
    return PsiElement.EMPTY_ARRAY;
  }
}
