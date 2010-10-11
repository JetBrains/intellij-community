package org.jetbrains.javafx.lang.parser;

import com.intellij.psi.tree.TokenSet;
import org.jetbrains.javafx.lang.JavaFxElementType;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.psi.impl.*;

/**
 * JavaFx element types
 *
 * @author Alexey.Ivanov
 */
public interface JavaFxElementTypes extends JavaFxStubElementTypes, JavaFxTokenTypes {

  JavaFxElementType IMPORT_STATEMENT = new JavaFxElementType("IMPORT_STATEMENT", JavaFxImportStatementImpl.class);
  JavaFxElementType INIT_BLOCK = new JavaFxElementType("INIT_BLOCK", JavaFxInitBlockImpl.class);
  JavaFxElementType POSTINIT_BLOCK = new JavaFxElementType("POSTINIT_BLOCK", JavaFxPostinitBlockImpl.class);
  JavaFxElementType TYPE_ELEMENT = new JavaFxElementType("TYPE_ELEMENT", JavaFxTypeElementImpl.class);
  JavaFxElementType FUNCTION_TYPE_ELEMENT = new JavaFxElementType("FUNCTION_TYPE_ELEMENT", JavaFxFunctionTypeElementImpl.class);
  JavaFxElementType MODIFIER_LIST = new JavaFxElementType("MODIFIER_LIST", JavaFxModifierListImpl.class);

  JavaFxElementType INSERT_EXPRESSION = new JavaFxElementType("INSERT_EXPRESSION", JavaFxInsertExpressionImpl.class);
  JavaFxElementType DELETE_EXPRESSION = new JavaFxElementType("DELETE_EXPRESSION", JavaFxDeleteExpressionImpl.class);
  JavaFxElementType WHILE_EXPRESSION = new JavaFxElementType("WHILE_EXPRESSION", JavaFxWhileExpressionImpl.class);
  JavaFxElementType BREAK_EXPRESSION = new JavaFxElementType("BREAK_EXPRESSION", JavaFxBreakExpressionImpl.class);
  JavaFxElementType CONTINUE_EXPRESSION = new JavaFxElementType("CONTINUE_EXPRESSION", JavaFxContinueExpressionImpl.class);
  JavaFxElementType THROW_EXPRESSION = new JavaFxElementType("THROW_EXPRESSION", JavaFxThrowExpressionImpl.class);
  JavaFxElementType RETURN_EXPRESSION = new JavaFxElementType("RETURN_EXPRESSION", JavaFxReturnExpressionImpl.class);
  JavaFxElementType TRY_EXPRESSION = new JavaFxElementType("TRY_EXPRESSION", JavaFxTryExpressionImpl.class);
  JavaFxElementType CATCH_CLAUSE = new JavaFxElementType("CATCH_CLAUSE", JavaFxCatchClauseImpl.class);
  JavaFxElementType FINALLY_CLAUSE = new JavaFxElementType("FINALLY_CLAUSE", JavaFxFinallyClauseImpl.class);
  JavaFxElementType INVALIDATE_EXPRESSION = new JavaFxElementType("INVALIDATE_EXPRESSION", JavaFxInvalidateExpressionImpl.class);

  JavaFxElementType IF_EXPRESSION = new JavaFxElementType("IF_EXPRESSION", JavaFxIfExpressionImpl.class);
  JavaFxElementType FOR_EXPRESSION = new JavaFxElementType("FOR_EXPRESSION", JavaFxForExpressionImpl.class);
  JavaFxElementType IN_CLAUSE = new JavaFxElementType("IN_CLAUSE", JavaFxInClauseImpl.class);
  JavaFxElementType NEW_EXPRESSION = new JavaFxElementType("NEW_EXPRESSION", JavaFxNewExpressionImpl.class);
  JavaFxElementType ON_REPLACE_CLAUSE = new JavaFxElementType("ON_REPLACE_CLAUSE", JavaFxOnReplaceClauseImpl.class);
  JavaFxElementType ON_INVALIDATE_CLAUSE = new JavaFxElementType("ON_INVALIDATE_CLAUSE", JavaFxOnInvalidateClauseImpl.class);

  JavaFxElementType ASSIGNMENT_EXPRESSION = new JavaFxElementType("ASSIGNMENT_EXPRESSION", JavaFxAssignmentExpressionImpl.class);
  JavaFxElementType BINARY_EXPRESSION = new JavaFxElementType("BINARY_EXPRESSION", JavaFxBinaryExpressionImpl.class);
  JavaFxElementType TYPE_EXPRESSION = new JavaFxElementType("TYPE_EXPRESSION", JavaFxTypeExpressionImpl.class);
  JavaFxElementType INDEXOF_EXPRESSION = new JavaFxElementType("INDEXOF_EXPRESSION", JavaFxIndexofExpressionImpl.class);
  JavaFxElementType UNARY_EXPRESSION = new JavaFxElementType("UNARY_EXPRESSION", JavaFxUnaryExpressionImpl.class);
  JavaFxElementType SUFFIXED_EXPRESSION = new JavaFxElementType("SUFFIXED_EXPRESSION", JavaFxSuffixedExpressionImpl.class);
  JavaFxElementType CALL_EXPRESSION = new JavaFxElementType("CALL_EXPRESSION", JavaFxCallExpressionImpl.class);
  JavaFxElementType EXPRESSION_LIST = new JavaFxElementType("EXPRESSION_LIST", JavaFxExpressionListImpl.class);
  JavaFxElementType INDEX_EXPRESSION = new JavaFxElementType("INDEX_EXPRESSION", JavaFxIndexExpressionImpl.class);
  JavaFxElementType SEQUENCE_SELECT_EXPRESSION =
    new JavaFxElementType("SEQUENCE_SELECT_EXPRESSION", JavaFxSequenceSelectExpressionImpl.class);
  JavaFxElementType SLICE_EXPRESSION = new JavaFxElementType("SLICE_EXPRESSION", JavaFxSliceExpressionImpl.class);
  JavaFxElementType REFERENCE_ELEMENT = new JavaFxElementType("REFERENCE_ELEMENT", JavaFxReferenceElementImpl.class);
  JavaFxElementType REFERENCE_LIST = new JavaFxElementType("REFERENCE_LIST", JavaFxReferenceListImpl.class);
  JavaFxElementType REFERENCE_EXPRESSION = new JavaFxElementType("REFERENCE_EXPRESSION", JavaFxReferenceExpressionImpl.class);

  JavaFxElementType OBJECT_LITERAL = new JavaFxElementType("OBJECT_LITERAL", JavaFxObjectLiteralImpl.class);
  JavaFxElementType SEQUENCE_LITERAL = new JavaFxElementType("SEQUENCE_LITERAL", JavaFxSequenceLiteralImpl.class);
  JavaFxElementType RANGE_EXPRESSION = new JavaFxElementType("RANGE_EXPRESSION", JavaFxRangeExpressionImpl.class);
  JavaFxElementType LITERAL_EXPRESSION = new JavaFxElementType("LITERAL", JavaFxLiteralExpressionImpl.class);
  JavaFxElementType THIS_EXPRESSION = new JavaFxElementType("THIS_EXPRESSION", JavaFxThisReferenceExpressionImpl.class);
  JavaFxElementType OBJECT_LITERAL_INIT = new JavaFxElementType("OBJECT_LITERAL_INIT", JavaFxObjectLiteralInitImpl.class);

  JavaFxElementType FUNCTION_EXPRESSION = new JavaFxElementType("FUNCTION_EXPRESSION", JavaFxFunctionExpressionImpl.class);
  JavaFxElementType STRING_EXPRESSION = new JavaFxElementType("STRING_EXPRESSION", JavaFxStringExpressionImpl.class);
  JavaFxElementType STRING_ELEMENT = new JavaFxElementType("STRING_ELEMENT", JavaFxStringCompoundElementImpl.class);
  JavaFxElementType BLOCK_EXPRESSION = new JavaFxElementType("BLOCK_EXPRESSION", JavaFxBlockExpressionImpl.class);
  JavaFxElementType PARENTHESIZED_EXPRESSION = new JavaFxElementType("PARENTHESIZED_EXPRESSION", JavaFxParenthesizedExpressionImpl.class);
  JavaFxElementType TIMELINE_EXPRESSION = new JavaFxElementType("TIMELINE_EXPRESSION", JavaFxTimelineExpressionImpl.class);
  JavaFxElementType BOUND_EXPRESSION = new JavaFxElementType("BOUND_EXPRESSION", JavaFxBoundExpressionImpl.class);

  TokenSet EXPRESSIONS = TokenSet.create(INSERT_EXPRESSION, DELETE_EXPRESSION, WHILE_EXPRESSION,
                                         BREAK_EXPRESSION, CONTINUE_EXPRESSION, THROW_EXPRESSION,
                                         RETURN_EXPRESSION, TRY_EXPRESSION, IF_EXPRESSION, FOR_EXPRESSION,
                                         NEW_EXPRESSION, VARIABLE_DECLARATION, ASSIGNMENT_EXPRESSION, BINARY_EXPRESSION,
                                         TYPE_EXPRESSION, INDEXOF_EXPRESSION, SUFFIXED_EXPRESSION, CALL_EXPRESSION,
                                         INDEX_EXPRESSION, SEQUENCE_SELECT_EXPRESSION, SLICE_EXPRESSION,
                                         REFERENCE_EXPRESSION, OBJECT_LITERAL, SEQUENCE_LITERAL, RANGE_EXPRESSION,
                                         LITERAL_EXPRESSION, FUNCTION_EXPRESSION, BLOCK_EXPRESSION, PARENTHESIZED_EXPRESSION,
                                         TIMELINE_EXPRESSION, THIS_EXPRESSION, INVALIDATE_EXPRESSION, BOUND_EXPRESSION);

  TokenSet CLASS_MEMBERS = TokenSet.create(INIT_BLOCK, POSTINIT_BLOCK, VARIABLE_DECLARATION, FUNCTION_DEFINITION);
  TokenSet DEFINITIONS = TokenSet.orSet(EXPRESSIONS, TokenSet.create(CLASS_DEFINITION, FUNCTION_DEFINITION));
  TokenSet TOP_LEVEL_ELEMENTS =
    TokenSet.orSet(DEFINITIONS, TokenSet.create(PACKAGE_DEFINITION, IMPORT_LIST));
  TokenSet TYPE_ELEMENTS = TokenSet.create(TYPE_ELEMENT, FUNCTION_TYPE_ELEMENT);
}
