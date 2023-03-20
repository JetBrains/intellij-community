// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.PatternContextInfo;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public final class MatcherImplUtil {

  public static PsiElement @NotNull [] createTreeFromText(@NotNull String text,
                                                          @NotNull PatternTreeContext context,
                                                          @NotNull LanguageFileType fileType,
                                                          @NotNull Project project) {
    return createTreeFromText(text, new PatternContextInfo(context), fileType, fileType.getLanguage(), project, false);
  }

  public static PsiElement @NotNull [] createSourceTreeFromText(@NotNull String text,
                                                                @NotNull PatternTreeContext context,
                                                                @NotNull LanguageFileType fileType,
                                                                @NotNull Project project,
                                                                boolean physical) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(fileType.getLanguage());
    if (profile != null) {
      return profile.createPatternTree(text, context, fileType, fileType.getLanguage(), null, project, physical);
    }
    return PsiElement.EMPTY_ARRAY;
  }

  public static PsiElement @NotNull [] createTreeFromText(@NotNull String text,
                                                          @NotNull PatternContextInfo contextInfo,
                                                          @NotNull LanguageFileType fileType,
                                                          @NotNull Language language,
                                                          @NotNull Project project,
                                                          boolean physical) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
    if (profile != null) {
      return profile.createPatternTree(text, contextInfo, fileType, language, project, physical);
    }
    return PsiElement.EMPTY_ARRAY;
  }
}
