// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.IGroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.GroovyDocPsiCreator;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrPatternVariableImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrArrayInitializerImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAttributeExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrDoWhileStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrExpressionLambdaBodyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrExpressionListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrImportAliasImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrInExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrLambdaExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMethodReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTryResourceListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyASTPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrListOrMapImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrThrowsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationArrayInitializerImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationNameValuePairImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrBlockStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrCatchClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrClassInitializerImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrConstructorInvocationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFinallyClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrForStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrIfStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrLabeledStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrSwitchStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrSynchronizedStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrTryCatchStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrWhileStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentLabelImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrNamedArgumentImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrSpreadArgumentImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.GrAssertStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.GrBreakStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.GrContinueStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.GrReturnStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.GrThrowStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.GrYieldStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrCaseSectionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrForInClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrTraditionalForClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrApplicationStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrArrayDeclarationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrAssignmentExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrCommandArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrConditionalExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrElvisExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrNewExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrParenthesizedExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrSwitchExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrTupleAssignmentExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrTupleImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrAdditiveExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrMultiplicativeExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrPowerExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrShiftExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrUnaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrBitwiseExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrRegexImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringContentImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringInjectionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.GrLogicalExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrIndexPropertyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrMethodCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrPropertySelectionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexFindExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrRelationalExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrBuiltinTypeClassExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrInstanceofExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrSafeCastExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrTypeCastExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnnotationTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnonymousClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumConstantInitializerImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrExtendsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrImplementsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrInterfaceDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrPermitsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrRecordDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrTraitTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrTypeDefinitionBodyBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrAnnotationMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrArrayTypeElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrBuiltInTypeElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClassTypeElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrDisjunctionTypeElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterParameterExtendsListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrWildcardTypeArgumentImpl;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ADDITIVE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION_ARGUMENT_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION_ARRAY_VALUE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION_METHOD;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANNOTATION_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ANONYMOUS_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.APPLICATION_ARGUMENT_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.APPLICATION_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.APPLICATION_INDEX;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ARGUMENT_LABEL;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ARGUMENT_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ARRAY_DECLARATION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ARRAY_INITIALIZER;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ARRAY_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ASSERT_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ASSIGNMENT_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.AS_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ATTRIBUTE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BAND_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BLOCK_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BOR_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BREAK_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BUILT_IN_TYPE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CASE_SECTION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CAST_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CATCH_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_BODY;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_INITIALIZER;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CLASS_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CODE_REFERENCE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CONSTRUCTOR;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CONSTRUCTOR_CALL_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.CONTINUE_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.DISJUNCTION_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.DOLLAR_SLASHY_LITERAL;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.DO_WHILE_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ELVIS_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_BODY;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_CONSTANT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_CONSTANTS;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_CONSTANT_INITIALIZER;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.ENUM_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.EQUALITY_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.EXPRESSION_LAMBDA_BODY;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.EXPRESSION_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.EXTENDS_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FIELD;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FINALLY_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FOR_IN_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.FOR_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.GSTRING;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IF_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IMPLEMENTS_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IMPL_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IMPORT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IMPORT_ALIAS;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.INDEX_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.INSTANCEOF_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.INTERFACE_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.IN_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LABELED_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LAMBDA_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LAND_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LIST_OR_MAP;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LITERAL;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LOR_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.METHOD;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.METHOD_CALL_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.METHOD_REFERENCE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.MODIFIER_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.MULTIPLICATIVE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.NAMED_ARGUMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.NEW_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PACKAGE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PARAMETER;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PARAMETER_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PARENTHESIZED_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PATTERN_VARIABLE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PERMITS_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.POWER_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PRIMITIVE_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.PROPERTY_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RANGE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RECORD_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REFERENCE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REGEX;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REGEX_FIND_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.REGEX_MATCH_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RELATIONAL_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RETURN_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SHIFT_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SLASHY_LITERAL;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SPREAD_LIST_ARGUMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_CONTENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_INJECTION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SWITCH_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SWITCH_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.SYNCHRONIZED_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TERNARY_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.THROWS_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.THROW_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TRADITIONAL_FOR_CLAUSE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TRAIT_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TRY_RESOURCE_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TRY_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TUPLE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TUPLE_ASSIGNMENT_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TYPE_ARGUMENT_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TYPE_PARAMETER;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TYPE_PARAMETER_BOUNDS_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.TYPE_PARAMETER_LIST;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.UNARY_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.VARIABLE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.VARIABLE_DECLARATION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.WHILE_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.WILDCARD_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.XOR_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.YIELD_STATEMENT;

/**
 * Creates Groovy PSI elements from the supplied AST nodes
 *
 * @author Dmitry.Krasilschikov
 */
public final class GroovyPsiCreator {

  /**
   * Creates a Groovy PSI element from the supplied AST node
   *
   * @param node Given node
   * @return Respective PSI element
   */
  public static PsiElement createElement(ASTNode node) {
    IElementType elem = node.getElementType();

    if (elem instanceof GroovyElementType.PsiCreator) {
      return ((GroovyElementType.PsiCreator)elem).createPsi(node);
    }

    if (elem instanceof IGroovyDocElementType) {
      return GroovyDocPsiCreator.createElement(node);
    }

    if (elem == MODIFIER_LIST) return new GrModifierListImpl(node);
    if (elem == ANNOTATION) return new GrAnnotationImpl(node);
    if (elem == ANNOTATION_ARGUMENT_LIST) return new GrAnnotationArgumentListImpl(node);
    if (elem == ANNOTATION_ARRAY_VALUE) return new GrAnnotationArrayInitializerImpl(node);
    if (elem == ANNOTATION_MEMBER_VALUE_PAIR) return new GrAnnotationNameValuePairImpl(node);

    // Imports
    if (elem == IMPORT) return new GrImportStatementImpl(node);
    if (elem == IMPORT_ALIAS) return new GrImportAliasImpl(node);

    // Packaging
    if (elem == PACKAGE_DEFINITION) return new GrPackageDefinitionImpl(node);

    //statements
    if (elem == LABELED_STATEMENT) return new GrLabeledStatementImpl(node);
    if (elem == IF_STATEMENT) return new GrIfStatementImpl(node);
    if (elem == FOR_STATEMENT) return new GrForStatementImpl(node);
    if (elem == FOR_IN_CLAUSE) return new GrForInClauseImpl(node);
    if (elem == TRADITIONAL_FOR_CLAUSE) return new GrTraditionalForClauseImpl(node);
    if (elem == EXPRESSION_LIST) return new GrExpressionListImpl(node);
    if (elem == WHILE_STATEMENT) return new GrWhileStatementImpl(node);
    if (elem == DO_WHILE_STATEMENT) return new GrDoWhileStatementImpl(node);
    if (elem == TRY_STATEMENT) return new GrTryCatchStatementImpl(node);
    if (elem == TRY_RESOURCE_LIST) return new GrTryResourceListImpl(node);
    if (elem == CATCH_CLAUSE) return new GrCatchClauseImpl(node);
    if (elem == FINALLY_CLAUSE) return new GrFinallyClauseImpl(node);
    if (elem == SYNCHRONIZED_STATEMENT) return new GrSynchronizedStatementImpl(node);
    if (elem == SWITCH_STATEMENT) return new GrSwitchStatementImpl(node);
    if (elem == CASE_SECTION) return new GrCaseSectionImpl(node);
    if (elem == VARIABLE_DECLARATION) return new GrVariableDeclarationImpl(node);
    if (elem == TUPLE) return new GrTupleImpl(node);
    if (elem == VARIABLE) return new GrVariableImpl(node);
    if (elem == PATTERN_VARIABLE) return new GrPatternVariableImpl(node);

    //type definitions
    if (elem == CLASS_TYPE_DEFINITION) return new GrClassDefinitionImpl(node);
    if (elem == INTERFACE_TYPE_DEFINITION) return new GrInterfaceDefinitionImpl(node);
    if (elem == ENUM_TYPE_DEFINITION) return new GrEnumTypeDefinitionImpl(node);
    if (elem == ANNOTATION_TYPE_DEFINITION) return new GrAnnotationTypeDefinitionImpl(node);
    if (elem == TRAIT_TYPE_DEFINITION) return new GrTraitTypeDefinitionImpl(node);
    if (elem == RECORD_TYPE_DEFINITION) return new GrRecordDefinitionImpl(node);
    if (elem == ANNOTATION_METHOD) return new GrAnnotationMethodImpl(node);
    if (elem == ANONYMOUS_TYPE_DEFINITION) return new GrAnonymousClassDefinitionImpl(node);

    if (elem == CODE_REFERENCE) return new GrCodeReferenceElementImpl(node);

    //clauses
    if (elem == EXTENDS_CLAUSE) return new GrExtendsClauseImpl(node);
    if (elem == IMPLEMENTS_CLAUSE) return new GrImplementsClauseImpl(node);
    if (elem == PERMITS_CLAUSE) return new GrPermitsClauseImpl(node);
    if (elem == THROWS_CLAUSE) return new GrThrowsClauseImpl(node);

    //bodies
    if (elem == CLASS_BODY) return new GrTypeDefinitionBodyBase.GrClassBody(node);
    if (elem == ENUM_BODY) return new GrTypeDefinitionBodyBase.GrEnumBody(node);
    if (elem == BLOCK_STATEMENT) return new GrBlockStatementImpl(node);
    if (elem == CONSTRUCTOR_CALL_EXPRESSION) return new GrConstructorInvocationImpl(node);

    //enum
    if (elem == ENUM_CONSTANTS) return new GrEnumConstantListImpl(node);
    if (elem == ENUM_CONSTANT) return new GrEnumConstantImpl(node);

    //members
    if (elem == CLASS_INITIALIZER) return new GrClassInitializerImpl(node);
    if (elem == FIELD) return new GrFieldImpl(node);
    if (elem == CONSTRUCTOR) return new GrConstructorImpl(node);
    if (elem == METHOD) return new GrMethodImpl(node);

    //parameters
    if (elem == PARAMETER_LIST) return new GrParameterListImpl(node);
    if (elem == PARAMETER) return new GrParameterImpl(node);

    //type parameters
    if (elem == TYPE_PARAMETER_LIST) return new GrTypeParameterListImpl(node);
    if (elem == TYPE_PARAMETER) return new GrTypeParameterImpl(node);
    if (elem == TYPE_PARAMETER_BOUNDS_LIST) return new GrTypeParameterParameterExtendsListImpl(node);
    if (elem == TYPE_ARGUMENT_LIST) return new GrTypeArgumentListImpl(node);

    // types
    if (elem == PRIMITIVE_TYPE_ELEMENT) return new GrBuiltInTypeElementImpl(node);
    if (elem == CLASS_TYPE_ELEMENT) return new GrClassTypeElementImpl(node);
    if (elem == ARRAY_TYPE_ELEMENT) return new GrArrayTypeElementImpl(node);
    if (elem == DISJUNCTION_TYPE_ELEMENT) return new GrDisjunctionTypeElementImpl(node);
    if (elem == WILDCARD_TYPE_ELEMENT) return new GrWildcardTypeArgumentImpl(node);

    //Branch statements
    if (elem == RETURN_STATEMENT) return new GrReturnStatementImpl(node);
    if (elem == YIELD_STATEMENT) return new GrYieldStatementImpl(node);
    if (elem == THROW_STATEMENT) return new GrThrowStatementImpl(node);
    if (elem == ASSERT_STATEMENT) return new GrAssertStatementImpl(node);
    if (elem == BREAK_STATEMENT) return new GrBreakStatementImpl(node);
    if (elem == CONTINUE_STATEMENT) return new GrContinueStatementImpl(node);

    //expressions
    if (elem == LITERAL) return new GrLiteralImpl(node);
    if (elem == LIST_OR_MAP) return new GrListOrMapImpl(node);
    if (elem == ASSIGNMENT_EXPRESSION) return new GrAssignmentExpressionImpl(node);
    if (elem == TUPLE_ASSIGNMENT_EXPRESSION) return new GrTupleAssignmentExpressionImpl(node);
    if (elem == TERNARY_EXPRESSION) return new GrConditionalExprImpl(node);
    if (elem == ELVIS_EXPRESSION) return new GrElvisExprImpl(node);
    if (elem == LOR_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == LAND_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == IMPL_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == XOR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == BOR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == BAND_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == REGEX_MATCH_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == REGEX_FIND_EXPRESSION) return new GrRegexFindExpressionImpl(node);
    if (elem == EQUALITY_EXPRESSION) return new GrRelationalExpressionImpl(node);
    if (elem == RELATIONAL_EXPRESSION) return new GrRelationalExpressionImpl(node);
    if (elem == IN_EXPRESSION) return new GrInExpressionImpl(node);
    if (elem == SHIFT_EXPRESSION) return new GrShiftExpressionImpl(node);
    if (elem == RANGE_EXPRESSION) return new GrRangeExpressionImpl(node);
    if (elem == ADDITIVE_EXPRESSION) return new GrAdditiveExpressionImpl(node);
    if (elem == MULTIPLICATIVE_EXPRESSION) return new GrMultiplicativeExpressionImpl(node);
    if (elem == POWER_EXPRESSION) return new GrPowerExpressionImpl(node);
    if (elem == UNARY_EXPRESSION) return new GrUnaryExpressionImpl(node);
    if (elem == CAST_EXPRESSION) return new GrTypeCastExpressionImpl(node);
    if (elem == AS_EXPRESSION) return new GrSafeCastExpressionImpl(node);
    if (elem == INSTANCEOF_EXPRESSION) return new GrInstanceofExpressionImpl(node);
    if (elem == BUILT_IN_TYPE_EXPRESSION) return new GrBuiltinTypeClassExpressionImpl(node);
    if (elem == GSTRING) return new GrStringImpl(node);
    if (elem == REGEX) return new GrRegexImpl(node);
    if (elem == STRING_INJECTION) return new GrStringInjectionImpl(node);
    if (elem == STRING_CONTENT) return new GrStringContentImpl(node);
    if (elem == PARENTHESIZED_EXPRESSION) return new GrParenthesizedExpressionImpl(node);
    if (elem == NEW_EXPRESSION) return new GrNewExpressionImpl(node);
    if (elem == ENUM_CONSTANT_INITIALIZER) return new GrEnumConstantInitializerImpl(node);
    if (elem == ARRAY_DECLARATION) return new GrArrayDeclarationImpl(node);
    if (elem == ARRAY_INITIALIZER) return new GrArrayInitializerImpl(node);
    if (elem == LAMBDA_EXPRESSION) return new GrLambdaExpressionImpl(node);
    if (elem == EXPRESSION_LAMBDA_BODY) return new GrExpressionLambdaBodyImpl(node);
    if (elem == SWITCH_EXPRESSION) return new GrSwitchExpressionImpl(node);

    //Paths
    if (elem == REFERENCE_EXPRESSION) return new GrReferenceExpressionImpl(node);
    if (elem == ATTRIBUTE_EXPRESSION) return new GrAttributeExpressionImpl(node);
    if (elem == METHOD_REFERENCE_EXPRESSION) return new GrMethodReferenceExpressionImpl(node);
    if (elem == PROPERTY_EXPRESSION) return new GrPropertySelectionImpl(node);
    if (elem == METHOD_CALL_EXPRESSION) return new GrMethodCallExpressionImpl(node);
    if (elem == INDEX_EXPRESSION) return new GrIndexPropertyImpl(node);
    if (elem == APPLICATION_INDEX) return new GrIndexPropertyImpl(node);
    if (elem == APPLICATION_EXPRESSION) return new GrApplicationStatementImpl(node);
    if (elem == APPLICATION_ARGUMENT_LIST) return new GrCommandArgumentListImpl(node);

    // Arguments
    if (elem == ARGUMENT_LIST) return new GrArgumentListImpl(node);
    if (elem == NAMED_ARGUMENT) return new GrNamedArgumentImpl(node);
    if (elem == SPREAD_LIST_ARGUMENT) return new GrSpreadArgumentImpl(node);
    if (elem == ARGUMENT_LABEL) return new GrArgumentLabelImpl(node);

    if (elem == SLASHY_LITERAL) return new GroovyASTPsiElementImpl(node);
    if (elem == DOLLAR_SLASHY_LITERAL) return new GroovyASTPsiElementImpl(node);

    return new ASTWrapperPsiElement(node);
  }
}
