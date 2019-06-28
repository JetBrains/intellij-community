// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.template.GroovyTemplateContextType;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyStructuralSearchProfile extends StructuralSearchProfileBase {
  public static final PatternContext FILE_CONTEXT = new PatternContext("File", "Default");
  public static final PatternContext CLASS_CONTEXT = new PatternContext("Class", "Class Member");
  private static final List<PatternContext> PATTERN_CONTEXTS = ContainerUtil.immutableList(FILE_CONTEXT, CLASS_CONTEXT);

  private static final TokenSet VARIABLE_DELIMITERS = TokenSet.create(GroovyTokenTypes.mCOMMA, GroovyTokenTypes.mSEMI);

  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[]{"_$_____"};
  }

  @NotNull
  @Override
  public List<PatternContext> getPatternContexts() {
    return PATTERN_CONTEXTS;
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @NotNull
  @Override
  protected TokenSet getVariableDelimiters() {
    return VARIABLE_DELIMITERS;
  }

  @Override
  public PsiCodeFragment createCodeFragment(Project project, String text, String contextId) {
    return new GroovyCodeFragment(project, text);
  }

  @NotNull
  @Override
  public Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return GroovyTemplateContextType.class;
  }

  @NotNull
  @Override
  public String getContext(@NotNull String pattern, @Nullable Language language, String contextId) {
    return CLASS_CONTEXT.getId().equals(contextId)
           ? "class AAAAA { " + PATTERN_PLACEHOLDER + " }"
           : PATTERN_PLACEHOLDER;
  }

  @Override
  public boolean isIdentifier(@Nullable PsiElement element) {
    return element instanceof PsiIdentifier;
  }
}
