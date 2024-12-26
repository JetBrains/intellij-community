// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion.smartEnter.processors;

import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyPlainEnterProcessor extends SmartEnterProcessorWithFixers.FixEnterProcessor {
  private static @Nullable GrCodeBlock getControlStatementBlock(int caret, PsiElement element) {
    GrStatement body = null;

    if (element instanceof GrMethod) return ((GrMethod)element).getBlock();

    if (element instanceof GrMethodCall) {
      final GrClosableBlock[] arguments = ((GrMethodCall)element).getClosureArguments();
      if (arguments.length > 0) return arguments[0]; else return null;
    }

    if (element instanceof GrIfStatement) {
      body = ((GrIfStatement)element).getThenBranch();
      if (body != null && caret > body.getTextRange().getEndOffset()) {
        body = ((GrIfStatement)element).getElseBranch();
      }
    }
    else if (element instanceof GrWhileStatement) {
      body = ((GrWhileStatement)element).getBody();
    }
    else if (element instanceof GrForStatement) {
      body = ((GrForStatement)element).getBody();
    }

    if (body instanceof GrBlockStatement) {
      return ((GrBlockStatement)body).getBlock();
    }

    return null;
  }

  @Override
  public boolean doEnter(PsiElement psiElement, PsiFile file, @NotNull Editor editor, boolean modified) {
    GrCodeBlock block = getControlStatementBlock(editor.getCaretModel().getOffset(), psiElement);

    if (block != null) {
      PsiElement firstElement = block.getFirstChild().getNextSibling();

      final int offset = firstElement != null ? firstElement.getTextRange().getStartOffset() - 1 : block.getTextRange().getEndOffset();
      editor.getCaretModel().moveToOffset(offset);
    }

    plainEnter(editor);
    return true;
  }
}
