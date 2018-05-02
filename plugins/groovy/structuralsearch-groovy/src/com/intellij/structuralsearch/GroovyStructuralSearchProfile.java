// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.template.GroovyTemplateContextType;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyStructuralSearchProfile extends StructuralSearchProfileBase {
  public static final String FILE_CONTEXT = "File";
  public static final String CLASS_CONTEXT = "Class";

  private static final TokenSet VARIABLE_DELIMETERS = TokenSet.create(GroovyTokenTypes.mCOMMA, GroovyTokenTypes.mSEMI);

  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[]{"_$_____"};
  }

  @NotNull
  @Override
  public String[] getContextNames() {
    return new String[]{FILE_CONTEXT, CLASS_CONTEXT};
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @NotNull
  @Override
  protected TokenSet getVariableDelimiters() {
    return VARIABLE_DELIMETERS;
  }

  @Override
  public PsiCodeFragment createCodeFragment(Project project, String text, @Nullable PsiElement context) {
    GroovyCodeFragment result = new GroovyCodeFragment(project, text);
    result.setContext(context);
    return result;
  }

  @NotNull
  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return GroovyTemplateContextType.class;
  }

  @Override
  public String getContext(@NotNull String pattern, @Nullable Language language, String contextName) {
    return CLASS_CONTEXT.equals(contextName)
           ? "class AAAAA { " + PATTERN_PLACEHOLDER + " }"
           : PATTERN_PLACEHOLDER;
  }

  @Override
  public boolean isIdentifier(@Nullable PsiElement element) {
    return element instanceof PsiIdentifier;
  }
}
