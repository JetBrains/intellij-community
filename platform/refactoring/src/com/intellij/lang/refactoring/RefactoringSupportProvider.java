// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.refactoring;

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to control the operation of refactorings for
 * files in the language.
 */
public abstract class RefactoringSupportProvider {
  /**
   * Allows several providers to be available for the same language
   * @param context refactoring context
   * @return true if refactoring support is available in given context
   */
  public boolean isAvailable(@NotNull PsiElement context) { return true; }

  /**
   * Checks if the Safe Delete refactoring can be applied to the specified element
   * in the language. The Safe Delete refactoring also requires the plugin to implement
   * Find Usages functionality.
   *
   * @param element the element for which Safe Delete was invoked
   * @return true if Safe Delete is available, false otherwise.
   */
  public boolean isSafeDeleteAvailable(@NotNull PsiElement element) { return false; }

  /**
   * @see #getIntroduceVariableHandler(PsiElement)
   */
  @Nullable
  public RefactoringActionHandler getIntroduceVariableHandler() { return null; }

  /**
   * @return handler for introducing local variables in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getIntroduceVariableHandler(PsiElement element) {
    return getIntroduceVariableHandler(); 
  }

  /**
   * @return handler for extracting methods in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getExtractMethodHandler() { return null; }

  /**
   * @return handler for introducing constants in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getIntroduceConstantHandler() { return null; }

  /**
   * @return handler for introducing fields in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getIntroduceFieldHandler() { return null; }

  /**
   * @return handler for introducing parameters in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getIntroduceParameterHandler() { return null; }

  /**
   * @return handler for introducing functional parameters/closures in this language
   * @see ContextAwareActionHandler
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getIntroduceFunctionalParameterHandler() {
    return null;
  }

  /**
   * @return handler for introducing functional locals in this language
   * @see ContextAwareActionHandler
   * @see RefactoringActionHandler
   */
  public RefactoringActionHandler getIntroduceFunctionalVariableHandler() {
    return null;
  }

  /**
   * @return handler for pulling up members in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getPullUpHandler() { return null; }

  /**
   * @return handler for pushing down members in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getPushDownHandler() { return null; }

  /**
   * @return handler for extracting members to an interface in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getExtractInterfaceHandler() { return null; }

  /**
   * @return handler for extracting members to some module in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getExtractModuleHandler() { return null; }

  /**
   * @return handler for extracting super class in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getExtractSuperClassHandler() { return null; }

  /**
   * @return handler for changing signature in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public ChangeSignatureHandler getChangeSignatureHandler() { return null; }

  public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) { return false; }

  public boolean isInplaceIntroduceAvailable(@NotNull PsiElement element, PsiElement context) {
    return false;
  }

  /**
   * @return handler for extracting [delegate] class in this language
   * @see RefactoringActionHandler
   */
  @Nullable
  public RefactoringActionHandler getExtractClassHandler() {
    return null;
  }

  public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, @Nullable PsiElement context) {
    return false;
  }
}
