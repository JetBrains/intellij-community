// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.IGroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.GroovyDocPsiCreator;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrImportAliasImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyASTPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrListOrMapImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrThrowsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationArrayInitializerImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationNameValuePairImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentLabelImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrNamedArgumentImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrSpreadArgumentImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrCaseLabelImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrCaseSectionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrForInClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrTraditionalForClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrBitwiseExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.*;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrAnnotationMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.*;

/**
 * Creates Groovy PSI element by given AST node
 *
 * @author ilyas, Dmitry.Krasilschikov
 */
public class GroovyPsiCreator {

  /**
   * Creates Groovy PSI element by given AST node
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

    //Identifiers & literal
    if (elem == GroovyElementTypes.LITERAL) return new GrLiteralImpl(node);
//    if (elem.equals(IDENTIFIER)) return new GrIdentifierImpl(node);
    //Lists, maps etc...
    if (elem == GroovyElementTypes.LIST_OR_MAP) return new GrListOrMapImpl(node);

    if (elem == GroovyElementTypes.MODIFIERS) return new GrModifierListImpl(node);
    if (elem == GroovyElementTypes.ANNOTATION) return new GrAnnotationImpl(node);
    if (elem == GroovyElementTypes.ANNOTATION_ARGUMENTS) return new GrAnnotationArgumentListImpl(node);
    if (elem == GroovyElementTypes.ANNOTATION_ARRAY_INITIALIZER) return new GrAnnotationArrayInitializerImpl(node);
    if (elem == GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR) return new GrAnnotationNameValuePairImpl(node);

    //throws
    if (elem == GroovyElementTypes.THROW_CLAUSE) return new GrThrowsClauseImpl(node);

    // Imports
    if (elem == GroovyElementTypes.IMPORT_STATEMENT) return new GrImportStatementImpl(node);
    if (elem == GroovyElementTypes.IMPORT_ALIAS) return new GrImportAliasImpl(node);

    // Packaging
    if (elem == GroovyElementTypes.PACKAGE_DEFINITION) return new GrPackageDefinitionImpl(node);

    //statements
    if (elem == GroovyElementTypes.LABELED_STATEMENT) return new GrLabeledStatementImpl(node);
    if (elem == GroovyElementTypes.IF_STATEMENT) return new GrIfStatementImpl(node);
    if (elem == GroovyElementTypes.FOR_STATEMENT) return new GrForStatementImpl(node);
    if (elem == GroovyElementTypes.FOR_IN_CLAUSE) return new GrForInClauseImpl(node);
    if (elem == GroovyElementTypes.FOR_TRADITIONAL_CLAUSE) return new GrTraditionalForClauseImpl(node);
    if (elem == GroovyElementTypes.WHILE_STATEMENT) return new GrWhileStatementImpl(node);
    if (elem == GroovyElementTypes.TRY_BLOCK_STATEMENT) return new GrTryCatchStatementImpl(node);
    if (elem == GroovyElementTypes.CATCH_CLAUSE) return new GrCatchClauseImpl(node);
    if (elem == GroovyElementTypes.FINALLY_CLAUSE) return new GrFinallyClauseImpl(node);
    if (elem == GroovyElementTypes.SYNCHRONIZED_STATEMENT) return new GrSynchronizedStatementImpl(node);
    if (elem == GroovyElementTypes.SWITCH_STATEMENT) return new GrSwitchStatementImpl(node);
    if (elem == GroovyElementTypes.CASE_LABEL) return new GrCaseLabelImpl(node);
    if (elem == GroovyElementTypes.CASE_SECTION) return new GrCaseSectionImpl(node);
    if (elem == GroovyElementTypes.VARIABLE_DEFINITION || elem == GroovyElementTypes.VARIABLE_DEFINITION_ERROR) return new GrVariableDeclarationImpl(node);
    if (elem == GroovyElementTypes.TUPLE) return new GrTupleImpl(node);
    if (elem == GroovyElementTypes.VARIABLE) return new GrVariableImpl(node);

    if (elem == GroovyElementTypes.FIELD) return new GrFieldImpl(node);
    if (elem == GroovyElementTypes.CLASS_INITIALIZER) return new GrClassInitializerImpl(node);

    //type definitions
    if (elem == GroovyElementTypes.CLASS_DEFINITION) return new GrClassDefinitionImpl(node);
    if (elem == GroovyElementTypes.INTERFACE_DEFINITION) return new GrInterfaceDefinitionImpl(node);
    if (elem == GroovyElementTypes.ENUM_DEFINITION) return new GrEnumTypeDefinitionImpl(node);
    if (elem == GroovyElementTypes.ANNOTATION_DEFINITION) return new GrAnnotationTypeDefinitionImpl(node);
    if (elem == GroovyElementTypes.TRAIT_DEFINITION) return new GrTraitTypeDefinitionImpl(node);
    if (elem == GroovyElementTypes.ANNOTATION_METHOD) return new GrAnnotationMethodImpl(node);

    if (elem == GroovyElementTypes.REFERENCE_ELEMENT) return new GrCodeReferenceElementImpl(node);
    if (elem == GroovyElementTypes.CLASS_TYPE_ELEMENT) return new GrClassTypeElementImpl(node);

    //clauses
    if (elem == GroovyElementTypes.IMPLEMENTS_CLAUSE) return new GrImplementsClauseImpl(node);
    if (elem == GroovyElementTypes.EXTENDS_CLAUSE) return new GrExtendsClauseImpl(node);

    //bodies
    if (elem == GroovyElementTypes.CLASS_BODY) return new GrTypeDefinitionBodyBase.GrClassBody(node);
    if (elem == GroovyElementTypes.ENUM_BODY) return new GrTypeDefinitionBodyBase.GrEnumBody(node);
    if (elem == GroovyElementTypes.BLOCK_STATEMENT) return new GrBlockStatementImpl(node);
    if (elem == GroovyElementTypes.EXPLICIT_CONSTRUCTOR) return new GrConstructorInvocationImpl(node);

    //enum
    if (elem == GroovyElementTypes.ENUM_CONSTANTS) return new GrEnumConstantListImpl(node);
    if (elem == GroovyElementTypes.ENUM_CONSTANT) return new GrEnumConstantImpl(node);

    //members
    if (elem == GroovyElementTypes.CONSTRUCTOR_DEFINITION) return new GrConstructorImpl(node);
    if (elem == GroovyElementTypes.METHOD_DEFINITION) return new GrMethodImpl(node);

    //parameters
    if (elem == GroovyElementTypes.PARAMETERS_LIST) return new GrParameterListImpl(node);
    if (elem == GroovyElementTypes.PARAMETER) return new GrParameterImpl(node);

    //type parameters
    if (elem == GroovyElementTypes.TYPE_ARGUMENT) return new GrWildcardTypeArgumentImpl(node);
    if (elem == GroovyElementTypes.TYPE_ARGUMENTS) return new GrTypeArgumentListImpl(node);


    if (elem == GroovyElementTypes.TYPE_PARAMETER_LIST) return new GrTypeParameterListImpl(node);
    if (elem == GroovyElementTypes.TYPE_PARAMETER) return new GrTypeParameterImpl(node);
    if (elem == GroovyElementTypes.TYPE_PARAMETER_EXTENDS_BOUND_LIST) return new GrTypeParameterParameterExtendsListImpl(node);

    //Branch statements
    if (elem == GroovyElementTypes.RETURN_STATEMENT) return new GrReturnStatementImpl(node);
    if (elem == GroovyElementTypes.THROW_STATEMENT) return new GrThrowStatementImpl(node);
    if (elem == GroovyElementTypes.ASSERT_STATEMENT) return new GrAssertStatementImpl(node);
    if (elem == GroovyElementTypes.BREAK_STATEMENT) return new GrBreakStatementImpl(node);
    if (elem == GroovyElementTypes.CONTINUE_STATEMENT) return new GrContinueStatementImpl(node);

    //expressions
    if (elem == GroovyElementTypes.CALL_EXPRESSION) return new GrApplicationStatementImpl(node);
    if (elem == GroovyElementTypes.COMMAND_ARGUMENTS) return new GrCommandArgumentListImpl(node);
    if (elem == GroovyElementTypes.CONDITIONAL_EXPRESSION) return new GrConditionalExprImpl(node);
    if (elem == GroovyElementTypes.ELVIS_EXPRESSION) return new GrElvisExprImpl(node);
    if (elem == GroovyElementTypes.ASSIGNMENT_EXPRESSION) return new GrAssignmentExpressionImpl(node);
    if (elem == GroovyElementTypes.TUPLE_ASSIGNMENT_EXPRESSION) return new GrTupleAssignmentExpressionImpl(node);

    if (elem == GroovyElementTypes.LOGICAL_OR_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == GroovyElementTypes.LOGICAL_AND_EXPRESSION) return new GrLogicalExpressionImpl(node);

    if (elem == GroovyElementTypes.EXCLUSIVE_OR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == GroovyElementTypes.INCLUSIVE_OR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == GroovyElementTypes.AND_EXPRESSION) return new GrBitwiseExpressionImpl(node);

    if (elem == GroovyElementTypes.REGEX_MATCH_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == GroovyElementTypes.REGEX_FIND_EXPRESSION) return new GrRegexFindExpressionImpl(node);
    if (elem == GroovyElementTypes.EQUALITY_EXPRESSION) return new GrRelationalExpressionImpl(node);
    if (elem == GroovyElementTypes.RELATIONAL_EXPRESSION) return new GrRelationalExpressionImpl(node);
    if (elem == GroovyElementTypes.SHIFT_EXPRESSION) return new GrShiftExpressionImpl(node);
    if (elem == GroovyElementTypes.RANGE_EXPRESSION) return new GrRangeExpressionImpl(node);
    if (TokenSets.SHIFT_SIGNS.contains(elem)) return new GrOperationSignImpl(node);
    if (elem == GroovyElementTypes.ADDITIVE_EXPRESSION) return new GrAdditiveExpressionImpl(node);
    if (elem == GroovyElementTypes.MULTIPLICATIVE_EXPRESSION) return new GrMultiplicativeExpressionImpl(node);
    if (elem == GroovyElementTypes.POWER_EXPRESSION) return new GrPowerExpressionImpl(node);
    if (elem == GroovyElementTypes.POWER_EXPRESSION_SIMPLE) return new GrPowerExpressionImpl(node);
    if (elem == GroovyElementTypes.UNARY_EXPRESSION) return new GrUnaryExpressionImpl(node);
    if (elem == GroovyElementTypes.CAST_EXPRESSION) return new GrTypeCastExpressionImpl(node);
    if (elem == GroovyElementTypes.SAFE_CAST_EXPRESSION) return new GrSafeCastExpressionImpl(node);
    if (elem == GroovyElementTypes.INSTANCEOF_EXPRESSION) return new GrInstanceofExpressionImpl(node);
    if (elem == GroovyElementTypes.BUILT_IN_TYPE_EXPRESSION) return new GrBuiltinTypeClassExpressionImpl(node);
    if (elem == GroovyElementTypes.ARRAY_TYPE) return new GrArrayTypeElementImpl(node);
    if (elem == GroovyElementTypes.BUILT_IN_TYPE) return new GrBuiltInTypeElementImpl(node);
    if (elem == GroovyElementTypes.DISJUNCTION_TYPE_ELEMENT) return new GrDisjunctionTypeElementImpl(node);
    if (elem == GroovyElementTypes.GSTRING) return new GrStringImpl(node);
    if (elem == GroovyElementTypes.REGEX) return new GrRegexImpl(node);
    if (elem == GroovyElementTypes.GSTRING_INJECTION) return new GrStringInjectionImpl(node);
    if (elem == GroovyElementTypes.GSTRING_CONTENT) return new GrStringContentImpl(node);
    if (elem == GroovyElementTypes.REFERENCE_EXPRESSION) return new GrReferenceExpressionImpl(node);
    if (elem == GroovyElementTypes.PARENTHESIZED_EXPRESSION) return new GrParenthesizedExpressionImpl(node);
    if (elem == GroovyElementTypes.NEW_EXPRESSION) return new GrNewExpressionImpl(node);
    if (elem == GroovyElementTypes.ANONYMOUS_CLASS_DEFINITION) return new GrAnonymousClassDefinitionImpl(node);
    if (elem == GroovyElementTypes.ENUM_CONSTANT_INITIALIZER) return new GrEnumConstantInitializerImpl(node);
    if (elem == GroovyElementTypes.ARRAY_DECLARATOR) return new GrArrayDeclarationImpl(node);

    //Paths
    if (elem == GroovyElementTypes.PATH_PROPERTY_REFERENCE) return new GrPropertySelectionImpl(node);
    if (elem == GroovyElementTypes.PATH_METHOD_CALL) return new GrMethodCallExpressionImpl(node);
    if (elem == GroovyElementTypes.PATH_INDEX_PROPERTY) return new GrIndexPropertyImpl(node);

    // Arguments
    if (elem == GroovyElementTypes.ARGUMENTS) return new GrArgumentListImpl(node);
    if (elem == GroovyElementTypes.NAMED_ARGUMENT) return new GrNamedArgumentImpl(node);
    if (elem == GroovyElementTypes.SPREAD_ARGUMENT) return new GrSpreadArgumentImpl(node);
    if (elem == GroovyElementTypes.ARGUMENT_LABEL) return new GrArgumentLabelImpl(node);

    if (elem == GroovyTokenTypes.mREGEX_LITERAL || elem == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) return new GroovyASTPsiElementImpl(node);

    return new ASTWrapperPsiElement(node);
  }

}
