// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

public class GroovyInlineHandler implements InlineHandler {

  @Override
  public @Nullable Settings prepareInlineElement(final @NotNull PsiElement element, @Nullable Editor editor, boolean invokedOnReference) {
    if (element instanceof GrField) {
      return GrInlineFieldUtil.inlineFieldSettings((GrField)element, editor, invokedOnReference);
    }
    else if (element instanceof GrMethod) {
      return GroovyInlineMethodUtil.inlineMethodSettings((GrMethod)element, editor, invokedOnReference);
    }
    else {
      if (element instanceof GrTypeDefinition) {
        return null;      //todo inline to anonymous class, push members from super class
      }
    }

    if (element instanceof PsiMember) {
      String message = GroovyRefactoringBundle.message("cannot.inline.0.", getFullName(element));
      CommonRefactoringUtil.showErrorHint(element.getProject(), editor, message, "", HelpID.INLINE_FIELD);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }
    return null;
  }

  private static String getFullName(PsiElement psi) {
    final String name = DescriptiveNameUtil.getDescriptiveName(psi);
    return (UsageViewUtil.getType(psi) + " " + name).trim();
  }


  @Override
  public void removeDefinition(@NotNull PsiElement element, @NotNull Settings settings) {
    final PsiElement owner = element.getParent().getParent();
    if (element instanceof GrVariable && owner instanceof GrVariableDeclarationOwner) {
      ((GrVariableDeclarationOwner)owner).removeVariable(((GrVariable)element));
    }
    if (element instanceof GrMethod) {
      element.delete();
    }
  }

  @Override
  public @Nullable Inliner createInliner(@NotNull PsiElement element, @NotNull Settings settings) {
    if (element instanceof GrVariable) {
      return new GrVariableInliner((GrVariable)element, settings);
    }
    if (element instanceof GrMethod) {
      return new GroovyMethodInliner((GrMethod)element);
    }
    return null;
  }
}

