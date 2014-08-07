package com.intellij.structuralsearch.impl.matcher;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 19, 2004
 * Time: 6:56:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class MatcherImplUtil {

  public static void transform(MatchOptions options) {
    if (options.hasVariableConstraints()) return;
    PatternCompiler.transformOldPattern(options);
  }

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
