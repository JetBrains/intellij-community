// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.memberPullUp.JavaPullUpHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureHandler;
import org.jetbrains.plugins.groovy.refactoring.extract.method.GroovyExtractMethodHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.constant.GrIntroduceConstantHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GrIntroduceFieldHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterHandler;
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler;

/**
 * @author ilyas
 */
public class GroovyRefactoringSupportProvider extends RefactoringSupportProvider {

  public static final GroovyRefactoringSupportProvider INSTANCE = new GroovyRefactoringSupportProvider();

  @Override
  public boolean isSafeDeleteAvailable(@NotNull PsiElement element) {
    return element instanceof GrTypeDefinition ||
           element instanceof GrField ||
           element instanceof GrMethod;
  }

  /**
   * @return handler for introducing local variables in Groovy
   */
  @Override
  @Nullable
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new GrIntroduceVariableHandler();
  }

  @Override
  @Nullable
  public RefactoringActionHandler getExtractMethodHandler() {
    return new GroovyExtractMethodHandler();
  }

  @Override
  public ChangeSignatureHandler getChangeSignatureHandler() {
    return new GrChangeSignatureHandler();
  }

  @Override
  public boolean isInplaceRenameAvailable(@NotNull PsiElement elementToRename, PsiElement nameSuggestionContext) {
    //local vars & params renames GrVariableInplaceRenameHandler

    if (nameSuggestionContext != null && nameSuggestionContext.getContainingFile() != elementToRename.getContainingFile()) return false;
    if (!(elementToRename instanceof GrLabeledStatement)) {
      return false;
    }
    SearchScope useScope = PsiSearchHelper.getInstance(elementToRename.getProject()).getUseScope(elementToRename);
    if (!(useScope instanceof LocalSearchScope)) return false;
    PsiElement[] scopeElements = ((LocalSearchScope)useScope).getScope();
    if (scopeElements.length > 1) {
      return false;
    }

    PsiFile containingFile = elementToRename.getContainingFile();
    return PsiTreeUtil.isAncestor(containingFile, scopeElements[0], false);

  }

  @Override
  public boolean isInplaceIntroduceAvailable(@NotNull PsiElement element, PsiElement context) {
    if (context == null || context.getContainingFile() != element.getContainingFile()) return false;
    return true;
  }

  @Override
  public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, @Nullable PsiElement context) {
    if (context == null || context.getContainingFile() instanceof GroovyFile) return false;
    PsiElement parent = context.getParent();

    //don't try to inplace rename aliased imported references
    if (parent instanceof GrReferenceElement) {
      GroovyResolveResult result = ((GrReferenceElement)parent).advancedResolve();
      PsiElement fileResolveContext = result.getCurrentFileResolveContext();
      if (fileResolveContext instanceof GrImportStatement && ((GrImportStatement)fileResolveContext).isAliasedImport()) {
        return false;
      }
    }
    return element instanceof GrMember;
  }

  @Override
  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new GrIntroduceFieldHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new GrIntroduceParameterHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new GrIntroduceConstantHandler();
  }

  @Nullable
  @Override
  public RefactoringActionHandler getPullUpHandler() {
    return new JavaPullUpHandler();
  }
}
