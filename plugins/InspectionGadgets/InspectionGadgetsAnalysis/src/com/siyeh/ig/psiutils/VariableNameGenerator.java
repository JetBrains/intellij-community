// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A convenience helper class to generate unique name for new variable. To use it, call several by* methods in chain, then call
 * the {@link #generate(boolean)} method. The order of by* method calls matters: candidates registered by earlier calls are preferred.
 * It's recommended to have at least one {@link #byName(String...)} call with at least one non-null candidate as the last resort.
 */
public final class VariableNameGenerator {
  private final @NotNull JavaCodeStyleManager myManager;
  private final @NotNull PsiElement myContext;
  private final @NotNull VariableKind myKind;
  private final @NonNls Set<String> candidates = new LinkedHashSet<>();

  /**
   * Constructs a new generator
   * @param context the place where new variable will be declared
   * @param kind kind of variable to generate
   */
  public VariableNameGenerator(@NotNull PsiElement context, @NotNull VariableKind kind) {
    myManager = JavaCodeStyleManager.getInstance(context.getProject());
    myContext = context;
    myKind = kind;
  }

  /**
   * Adds name candidates based on type
   * @param type type of newly generated variable
   * @return this generator
   */
  public VariableNameGenerator byType(@Nullable PsiType type) {
    if (type != null) {
      SuggestedNameInfo info = myManager.suggestVariableName(myKind, null, null, type, true);
      candidates.addAll(Arrays.asList(info.names));
      if (type.equals(PsiTypes.intType())) {
        candidates.add("j");
        candidates.add("k");
      }
    }
    return this;
  }

  /**
   * Adds name candidates based on expression
   * @param expression expression which value will be stored to the new variable
   * @return this generator
   */
  public VariableNameGenerator byExpression(@Nullable PsiExpression expression) {
    if (expression != null) {
      SuggestedNameInfo info = myManager.suggestVariableName(myKind, null, expression, null, true);
      candidates.addAll(Arrays.asList(info.names));
    }
    return this;
  }

  /**
   * Adds name candidates based on collection/array name
   * @param name of the collection/array which element is represented by newly generated variable
   * @return this generator
   */
  public VariableNameGenerator byCollectionName(@Nullable String name) {
    if (name != null) {
      PsiExpression expr = JavaPsiFacade.getElementFactory(myContext.getProject()).createExpressionFromText(name + "[0]", myContext);
      byExpression(expr);
    }
    return this;
  }

  /**
   * Adds name candidates based on property name
   * @param names base names which could be used to generate variable name
   * @return this generator
   */
  public VariableNameGenerator byName(@NonNls String... names) {
    for (String name : names) {
      if (name != null) {
        SuggestedNameInfo info = myManager.suggestVariableName(myKind, name, null, null, true);
        candidates.addAll(Arrays.asList(info.names));
      }
    }
    return this;
  }

  /**
   * Generates and returns the unique name
   * @param lookForward whether further conflicting declarations should be considered 
   * @return a generated variable name
   */
  @NotNull
  public String generate(boolean lookForward) {
    String suffixed = null;
    @NonNls final Set<String> candidates = this.candidates.isEmpty() ? Collections.singleton("v") : this.candidates;
    for (String candidate : candidates) {
      String name = myManager.suggestUniqueVariableName(candidate, myContext, lookForward);
      if (name.equals(candidate)) return name;
      if (suffixed == null) {
        suffixed = name;
      }
    }
    return suffixed;
  }

  @NotNull
  public List<String> generateAll(boolean lookForward) {
    List<String> suffixed = new ArrayList<>();
    List<String> result = new ArrayList<>();
    final @NonNls String defaultVariableName = "v";
    for (String candidate : candidates.isEmpty() ? Collections.singleton(defaultVariableName) : candidates) {
      String name = myManager.suggestUniqueVariableName(candidate, myContext, lookForward);
      if (name.equals(candidate)) result.add(name);
      else suffixed.add(name);
    }
    result.addAll(suffixed);
    return result;
  }
}
