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
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Maxim.Mossienko
 */
public class MatcherImplUtil {

  public static PsiElement[] createTreeFromText(String text, PatternTreeContext context, FileType fileType, Project project)
    throws IncorrectOperationException {
    return createTreeFromText(text, context, fileType, null, null, project, false);
  }

  public static PsiElement[] createSourceTreeFromText(String text,
                                                      PatternTreeContext context,
                                                      FileType fileType,
                                                      String extension,
                                                      Project project,
                                                      boolean physical) {
    if (fileType instanceof LanguageFileType) {
      Language language = ((LanguageFileType)fileType).getLanguage();
      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
      if (profile != null) {
        return profile.createPatternTree(text, context, fileType, null, null, extension, project, physical);
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  public static PsiElement[] createTreeFromText(String text,
                                                PatternTreeContext context,
                                                FileType fileType,
                                                Language language,
                                                String contextName,
                                                Project project,
                                                boolean physical) throws IncorrectOperationException {
    if (language == null && fileType instanceof LanguageFileType) {
      language = ((LanguageFileType)fileType).getLanguage();
    }
    if (language != null) {
      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByLanguage(language);
      if (profile != null) {
        return profile.createPatternTree(text, context, fileType, language, contextName, null, project, physical);
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }
}
