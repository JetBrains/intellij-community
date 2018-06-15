// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.formatter.blocks.ClosureBodyBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.GrLabelBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParams;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrTryResourceList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;

import static com.intellij.formatting.Indent.*;
import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_LPAREN;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_RPAREN;

/**
 * @author ilyas
 */
public class GroovyIndentProcessor extends GroovyElementVisitor {
  public static final int GDOC_COMMENT_INDENT = 1;
  private static final TokenSet GSTRING_TOKENS_INNER = TokenSet.create(GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mGSTRING_END,
                                                                       GroovyTokenTypes.mDOLLAR);

  private Indent myResult = null;

  private IElementType myChildType;
  private GroovyBlock myBlock;
  private PsiElement myChild;

  /**
   * Calculates indent, based on code style, between parent block and child node
   *
   * @param parentBlock parent block
   * @param child       child node
   * @return indent
   */
  @NotNull
  public Indent getChildIndent(@NotNull final GroovyBlock parentBlock, @NotNull final ASTNode child) {
    myChildType = child.getElementType();
    if (parentBlock instanceof ClosureBodyBlock) {
      if (myChildType == GroovyElementTypes.PARAMETERS_LIST) {
        return getNoneIndent();
      }
      else if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
        return getNormalIndent();
      }
    }
    if (parentBlock instanceof GrLabelBlock) {
      ASTNode first = parentBlock.getNode().getFirstChildNode();
      return child == first
             ? getNoneIndent()
             : getLabelIndent();
    }

    if (GSTRING_TOKENS_INNER.contains(myChildType)) {
      return getAbsoluteNoneIndent();
    }

    final PsiElement parent = parentBlock.getNode().getPsi();
    if (parent instanceof GroovyPsiElement) {
      myBlock = parentBlock;
      myChild = child.getPsi();
      ((GroovyPsiElement)parent).accept(this);
      if (myResult != null) return myResult;
    }

    return getNoneIndent();
  }

  @Override
  public void visitAssertStatement(@NotNull GrAssertStatement assertStatement) {
    if (myChildType != GroovyTokenTypes.kASSERT) {
      myResult = getContinuationIndent();
    }
  }

  @Override
  public void visitAnnotationArrayInitializer(@NotNull GrAnnotationArrayInitializer arrayInitializer) {
    if (myChildType != GroovyTokenTypes.mLBRACK && myChildType != GroovyTokenTypes.mRBRACK) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitListOrMap(@NotNull GrListOrMap listOrMap) {
    if (myChildType != GroovyTokenTypes.mLBRACK && myChildType != GroovyTokenTypes.mRBRACK) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitCaseSection(@NotNull GrCaseSection caseSection) {
    if (myChildType != GroovyElementTypes.CASE_LABEL) {
      myResult = getNormalIndent();
    }
  }

  @Override
  public void visitSwitchStatement(@NotNull GrSwitchStatement switchStatement) {
    if (myChildType == GroovyElementTypes.CASE_SECTION) {
      myResult = getSwitchCaseIndent(getGroovySettings());
    }
  }

  @Override
  public void visitLabeledStatement(@NotNull GrLabeledStatement labeledStatement) {
    if (myChildType == GroovyTokenTypes.mIDENT) {
      CommonCodeStyleSettings.IndentOptions indentOptions = myBlock.getContext().getSettings().getIndentOptions();
      if (indentOptions != null && indentOptions.LABEL_INDENT_ABSOLUTE) {
        myResult = getAbsoluteLabelIndent();
      }
      else if (!myBlock.getContext().getGroovySettings().INDENT_LABEL_BLOCKS) {
        myResult = getLabelIndent();
      }
    }
    else {
      if (myBlock.getContext().getGroovySettings().INDENT_LABEL_BLOCKS) {
        myResult = getLabelIndent();
      }
    }
  }

  @Override
  public void visitAnnotation(@NotNull GrAnnotation annotation) {
    if (myChildType == GroovyElementTypes.ANNOTATION_ARGUMENTS) {
      myResult = getContinuationIndent();
    }
    else {
      myResult = getNoneIndent();
    }
  }

  @Override
  public void visitArgumentList(@NotNull GrArgumentList list) {
    if (myChildType != GroovyTokenTypes.mLPAREN && myChildType != GroovyTokenTypes.mRPAREN) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitIfStatement(@NotNull GrIfStatement ifStatement) {
    if (TokenSets.BLOCK_SET.contains(myChildType)) {
      if (myChild == ifStatement.getCondition()) {
        myResult = getContinuationWithoutFirstIndent();
      }
    }
    else if (myChild == ifStatement.getThenBranch()) {
      myResult = getNormalIndent();
    }
    else if (myChild == ifStatement.getElseBranch()) {
      if (getGroovySettings().SPECIAL_ELSE_IF_TREATMENT && myChildType == GroovyElementTypes.IF_STATEMENT) {
        myResult = getNoneIndent();
      }
      else {
        myResult = getNormalIndent();
      }
    }
  }

  @Override
  public void visitAnnotationArgumentList(@NotNull GrAnnotationArgumentList annotationArgumentList) {
    if (myChildType == GroovyTokenTypes.mLPAREN || myChildType == GroovyTokenTypes.mRPAREN) {
      myResult = getNoneIndent();
    }
    else {
      myResult = getContinuationIndent();
    }
  }

  @Override
  public void visitNamedArgument(@NotNull GrNamedArgument argument) {
    if (myChild == argument.getExpression()) {
      myResult = getContinuationIndent();
    }
  }

  @Override
  public void visitVariable(@NotNull GrVariable variable) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitEnumConstant(@NotNull GrEnumConstant enumConstant) {
    getNoneIndent();
  }

  @Override
  public void visitDocComment(@NotNull GrDocComment comment) {
    if (myChildType != GroovyDocTokenTypes.mGDOC_COMMENT_START) {
      myResult = getSpaceIndent(GDOC_COMMENT_INDENT);
    }
  }

  @Override
  public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
    if (myChild instanceof GrVariable) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitDocTag(@NotNull GrDocTag docTag) {
    if (myChildType != GroovyDocTokenTypes.mGDOC_TAG_NAME) {
      myResult = getSpaceIndent(GDOC_COMMENT_INDENT);
    }
  }

  @Override
  public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitThrowsClause(@NotNull GrThrowsClause throwsClause) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitImplementsClause(@NotNull GrImplementsClause implementsClause) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitDocMethodParameterList(@NotNull GrDocMethodParams params) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitExtendsClause(@NotNull GrExtendsClause extendsClause) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitFile(@NotNull GroovyFileBase file) {
    myResult = getNoneIndent();
  }

  @Override
  public void visitMethod(@NotNull GrMethod method) {
    if (myChildType == GroovyElementTypes.PARAMETERS_LIST) {
      myResult = getContinuationIndent();
    }
    else if (myChildType == GroovyElementTypes.THROW_CLAUSE) {
      myResult = getGroovySettings().ALIGN_THROWS_KEYWORD ? getNoneIndent() : getContinuationIndent();
    } else if (myChildType == GroovyElementTypes.OPEN_BLOCK) {
      myResult = getBlockIndent(getGroovySettings().METHOD_BRACE_STYLE);
    }
  }

  @Override
  public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
    if (myChildType == GroovyElementTypes.EXTENDS_CLAUSE || myChildType == GroovyElementTypes.IMPLEMENTS_CLAUSE) {
      myResult = getContinuationIndent();
    }
    else if (myChildType == GroovyElementTypes.ENUM_BODY || myChildType == GroovyElementTypes.CLASS_BODY) {
      myResult = getBlockIndent(getGroovySettings().CLASS_BRACE_STYLE);
    }
  }

  @Override
  public void visitTypeDefinitionBody(@NotNull GrTypeDefinitionBody typeDefinitionBody) {
    if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
      myResult = getIndentInBlock(getGroovySettings().CLASS_BRACE_STYLE);
    }
  }

  @Override
  public void visitClosure(@NotNull GrClosableBlock closure) {
    if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
      myResult = getNormalIndent();
    }
  }

  @Override
  public void visitOpenBlock(@NotNull GrOpenBlock block) {
    final IElementType type = block.getNode().getElementType();
    if (type != GroovyElementTypes.OPEN_BLOCK && type != GroovyElementTypes.CONSTRUCTOR_BODY) return;

    int braceStyle;
    PsiElement parent = block.getParent();
    if (parent instanceof GrMethod) {
      braceStyle = getGroovySettings().METHOD_BRACE_STYLE;
    } else {
      braceStyle = getGroovySettings().BRACE_STYLE;
    }

    if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
      myResult = getIndentInBlock(braceStyle);
    }
  }

  @Override
  public void visitWhileStatement(@NotNull GrWhileStatement whileStatement) {
    if (myChild == whileStatement.getBody() && !TokenSets.BLOCK_SET.contains(myChildType)) {
      myResult = getNormalIndent();
    }
    else if (myChild == whileStatement.getCondition()) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitSynchronizedStatement(@NotNull GrSynchronizedStatement synchronizedStatement) {
    if (myChild == synchronizedStatement.getMonitor()) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitForStatement(@NotNull GrForStatement forStatement) {
    if (myChild == forStatement.getBody() && !TokenSets.BLOCK_SET.contains(myChildType)) {
      myResult = getNormalIndent();
    }
    else if (myChild == forStatement.getClause()) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  private CommonCodeStyleSettings getGroovySettings() {
    return myBlock.getContext().getSettings();
  }

  @Override
  public void visitParenthesizedExpression(@NotNull GrParenthesizedExpression expression) {
    if (myChildType == GroovyTokenTypes.mLPAREN || myChildType == GroovyTokenTypes.mRPAREN) {
      myResult = getNoneIndent();
    }
    else {
      myResult = getContinuationIndent();
    }
  }

  public static Indent getSwitchCaseIndent(@NotNull CommonCodeStyleSettings settings) {
    if (settings.INDENT_CASE_FROM_SWITCH) {
      return getIndentInBlock(settings.BRACE_STYLE);
    }
    else {
      return getNoneIndent();
    }
  }

  @Override
  public void visitParameterList(@NotNull GrParameterList parameterList) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitArrayDeclaration(@NotNull GrArrayDeclaration arrayDeclaration) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitExpression(@NotNull GrExpression expression) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitTypeArgumentList(@NotNull GrTypeArgumentList typeArgumentList) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitWildcardTypeArgument(@NotNull GrWildcardTypeArgument wildcardTypeArgument) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitAnnotationMethod(@NotNull GrAnnotationMethod annotationMethod) {
    if (myChild instanceof GrAnnotationMemberValue) {
      myResult = getContinuationIndent();
    }
    else {
      super.visitAnnotationMethod(annotationMethod);
    }
  }

  @Override
  public void visitAnnotationNameValuePair(@NotNull GrAnnotationNameValuePair nameValuePair) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitForInClause(@NotNull GrForInClause forInClause) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitForClause(@NotNull GrForClause forClause) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitCatchClause(@NotNull GrCatchClause catchClause) {
    if (myChild == catchClause.getBody()) {
      myResult = getBlockIndent(getGroovySettings().BRACE_STYLE);
    }
    else {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitTryStatement(@NotNull GrTryCatchStatement tryCatchStatement) {
    if (myChildType == GroovyElementTypes.OPEN_BLOCK) {
      myResult = getBlockIndent(getGroovySettings().BRACE_STYLE);
    }
  }

  @Override
  public void visitTryResourceList(@NotNull GrTryResourceList resourceList) {
    if (myChildType != T_LPAREN && myChildType != T_RPAREN) {
      myResult = getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitBlockStatement(@NotNull GrBlockStatement blockStatement) {
    myResult = getBlockIndent(getGroovySettings().BRACE_STYLE);
  }

  @Override
  public void visitFinallyClause(@NotNull GrFinallyClause catchClause) {
    if (myChildType == GroovyElementTypes.OPEN_BLOCK) {
      myResult = getBlockIndent(getGroovySettings().BRACE_STYLE);
    }
  }

  @Override
  public void visitTypeParameterList(@NotNull GrTypeParameterList list) {
    myResult = getContinuationWithoutFirstIndent();
  }

  @NotNull
  public static Indent getIndentInBlock(int braceStyle) {
      return braceStyle == NEXT_LINE_SHIFTED ? getNoneIndent() : getNormalIndent();
  }

  @NotNull
  public static Indent getBlockIndent(int braceStyle) {
    return braceStyle == NEXT_LINE_SHIFTED || braceStyle == NEXT_LINE_SHIFTED2 ? getNormalIndent() : getNoneIndent();
  }

  public static ChildAttributes getChildSwitchIndent(GrCaseSection psiParent, int newIndex) {
    Indent indent = isFinishedCase(psiParent, newIndex) ? getNoneIndent() : getNormalIndent();
    return new ChildAttributes(indent, null);
  }

  public static boolean isFinishedCase(GrCaseSection psiParent, int newIndex) {
    final PsiElement[] children = psiParent.getChildren();
    newIndex--;
    for (int i = 0; i < children.length && i < newIndex; i++) {
      PsiElement child = children[i];
      if (child instanceof GrBreakStatement ||
          child instanceof GrContinueStatement ||
          child instanceof GrReturnStatement ||
          child instanceof GrThrowStatement) {
        return true;
      }
    }
    return false;
  }
}

