// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GrDoWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

import static com.intellij.codeInsight.CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement;

/**
 * @author Max Medvedev
 */
public class GroovyBraceEnforcer extends GroovyRecursiveElementVisitor {
  private static final Logger LOG = Logger.getInstance(GroovyBraceEnforcer.class);

  private final PostFormatProcessorHelper myPostProcessor;

  public GroovyBraceEnforcer(CodeStyleSettings settings) {
    myPostProcessor = new PostFormatProcessorHelper(settings.getCommonSettings(GroovyLanguage.INSTANCE));
  }

  public TextRange processText(final GroovyFile source, final TextRange rangeToReformat) {
    myPostProcessor.setResultTextRange(rangeToReformat);
    source.accept(this);
    return myPostProcessor.getResultTextRange();
  }

  public PsiElement process(GroovyPsiElement formatted) {
    LOG.assertTrue(formatted.isValid());
    formatted.accept(this);
    return formatted;
  }

  private void replaceWithBlock(@NotNull GrStatement statement, GrStatement blockCandidate) {
    if (!statement.isValid()) {
      LOG.assertTrue(false);
    }

    if (!checkRangeContainsElement(blockCandidate)) return;

    final PsiManager manager = statement.getManager();
    LOG.assertTrue(manager != null);
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());

    String oldText = blockCandidate.getText();
    // There is a possible case that target block to wrap ends with single-line comment. Example:
    //     if (true) i = 1; // Cool assignment
    // We can't just surround target block of code with curly braces because the closing one will be treated as comment as well.
    // Hence, we perform a check if we have such situation at the moment and insert new line before the closing brace.
    StringBuilder buf = new StringBuilder(oldText.length() + 5);
    buf.append("{\n").append(oldText);
    buf.append("\n}");
    final int oldTextLength = statement.getTextLength();
    try {
      ASTNode newChild = SourceTreeToPsiMap.psiElementToTree(factory.createBlockStatementFromText(buf.toString(), null));
      ASTNode parent = SourceTreeToPsiMap.psiElementToTree(statement);
      ASTNode childToReplace = SourceTreeToPsiMap.psiElementToTree(blockCandidate);
      CodeEditUtil.replaceChild(parent, childToReplace, newChild);

      removeTailSemicolon(newChild, parent);
      statement = forcePsiPostprocessAndRestoreElement(statement);
      if (statement != null) {
        CodeStyleManager.getInstance(statement.getProject()).reformat(statement, true);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      updateResultRange(oldTextLength, statement.getTextLength());
    }
  }

  private static void removeTailSemicolon(ASTNode newChild, ASTNode parent) {
    ASTNode semi = newChild.getTreeNext();
    while (semi != null && semi.getElementType() == TokenType.WHITE_SPACE && !semi.getText().contains("\n")) {
      semi = semi.getTreeNext();
    }

    if (semi != null && semi.getElementType() == GroovyTokenTypes.mSEMI) {
      parent.removeRange(newChild.getTreeNext(), semi.getTreeNext());
    }
  }


  protected void updateResultRange(final int oldTextLength, final int newTextLength) {
    myPostProcessor.updateResultRange(oldTextLength, newTextLength);
  }

  protected boolean checkElementContainsRange(final PsiElement element) {
    return myPostProcessor.isElementPartlyInRange(element);
  }

  protected boolean checkRangeContainsElement(final PsiElement element) {
    return myPostProcessor.isElementFullyInRange(element);
  }

  private void processStatement(GrStatement statement, @Nullable GrStatement blockCandidate, int options) {
    if (blockCandidate instanceof GrCodeBlock || blockCandidate instanceof GrBlockStatement || blockCandidate == null) return;
    if (options == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
        options == CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE && PostFormatProcessorHelper.isMultiline(statement)) {
      replaceWithBlock(statement, blockCandidate);
    }
  }

  @Override
  public void visitIfStatement(@NotNull GrIfStatement statement) {
    if (checkElementContainsRange(statement)) {
      final SmartPsiElementPointer pointer =
        SmartPointerManager.getInstance(statement.getProject()).createSmartPsiElementPointer(statement);
      super.visitIfStatement(statement);
      statement = (GrIfStatement)pointer.getElement();
      if (statement == null) return;

      processStatement(statement, statement.getThenBranch(), myPostProcessor.getSettings().IF_BRACE_FORCE);
      final GrStatement elseBranch = statement.getElseBranch();
      if (!(elseBranch instanceof GrIfStatement) || !myPostProcessor.getSettings().SPECIAL_ELSE_IF_TREATMENT) {
        processStatement(statement, elseBranch, myPostProcessor.getSettings().IF_BRACE_FORCE);
      }
    }
  }

  @Override
  public void visitForStatement(@NotNull GrForStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitForStatement(statement);
      processStatement(statement, statement.getBody(), myPostProcessor.getSettings().FOR_BRACE_FORCE);
    }
  }

  @Override
  public void visitWhileStatement(@NotNull GrWhileStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitWhileStatement(statement);
      processStatement(statement, statement.getBody(), myPostProcessor.getSettings().WHILE_BRACE_FORCE);
    }
  }

  @Override
  public void visitDoWhileStatement(@NotNull GrDoWhileStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitDoWhileStatement(statement);
      processStatement(statement, statement.getBody(), myPostProcessor.getSettings().DOWHILE_BRACE_FORCE);
    }
  }
}
