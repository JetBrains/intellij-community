// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.groovy;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.PatternContext;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.template.GroovyTemplateContextType;

import java.util.List;

public class GroovyStructuralSearchProfile extends StructuralSearchProfileBase {
  public static final PatternContext FILE_CONTEXT = new PatternContext("File", () -> SSRBundle.message("pattern.context.default"));
  public static final PatternContext CLASS_CONTEXT = new PatternContext("Class", () -> SSRBundle.message("pattern.context.class.member"));
  private static final List<PatternContext> PATTERN_CONTEXTS = List.of(FILE_CONTEXT, CLASS_CONTEXT);

  private static final TokenSet VARIABLE_DELIMITERS = TokenSet.create(GroovyTokenTypes.mCOMMA, GroovyTokenTypes.mSEMI);

  @Override
  protected String @NotNull [] getVarPrefixes() {
    return new String[]{"_$_____"};
  }

  @Override
  public @NotNull List<PatternContext> getPatternContexts() {
    return PATTERN_CONTEXTS;
  }

  @Override
  public boolean isMyLanguage(@NotNull Language language) {
    return language == GroovyLanguage.INSTANCE;
  }

  @Override
  protected @NotNull TokenSet getVariableDelimiters() {
    return VARIABLE_DELIMITERS;
  }

  @Override
  public PsiCodeFragment createCodeFragment(@NotNull Project project, @NotNull String text, String contextId) {
    return new GroovyCodeFragment(project, text);
  }

  @Override
  public @NotNull Class<? extends TemplateContextType> getTemplateContextTypeClass() {
    return GroovyTemplateContextType.Generic.class;
  }

  @Override
  public @NotNull String getContext(@NotNull String pattern, @Nullable Language language, String contextId) {
    return CLASS_CONTEXT.getId().equals(contextId)
           ? "class AAAAA { " + PATTERN_PLACEHOLDER + " }"
           : PATTERN_PLACEHOLDER;
  }

  @Override
  public boolean isIdentifier(@Nullable PsiElement element) {
    return element instanceof PsiIdentifier;
  }
}
