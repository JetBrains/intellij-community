// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.IGroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.GroovyDocPsiCreator;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
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

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

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
    if (elem == WHILE_STATEMENT) return new GrWhileStatementImpl(node);
    if (elem == TRY_STATEMENT) return new GrTryCatchStatementImpl(node);
    if (elem == CATCH_CLAUSE) return new GrCatchClauseImpl(node);
    if (elem == FINALLY_CLAUSE) return new GrFinallyClauseImpl(node);
    if (elem == SYNCHRONIZED_STATEMENT) return new GrSynchronizedStatementImpl(node);
    if (elem == SWITCH_STATEMENT) return new GrSwitchStatementImpl(node);
    if (elem == CASE_LABEL) return new GrCaseLabelImpl(node);
    if (elem == CASE_SECTION) return new GrCaseSectionImpl(node);
    if (elem == VARIABLE_DECLARATION) return new GrVariableDeclarationImpl(node);
    if (elem == TUPLE) return new GrTupleImpl(node);
    if (elem == VARIABLE) return new GrVariableImpl(node);

    //type definitions
    if (elem == CLASS_TYPE_DEFINITION) return new GrClassDefinitionImpl(node);
    if (elem == INTERFACE_TYPE_DEFINITION) return new GrInterfaceDefinitionImpl(node);
    if (elem == ENUM_TYPE_DEFINITION) return new GrEnumTypeDefinitionImpl(node);
    if (elem == ANNOTATION_TYPE_DEFINITION) return new GrAnnotationTypeDefinitionImpl(node);
    if (elem == TRAIT_TYPE_DEFINITION) return new GrTraitTypeDefinitionImpl(node);
    if (elem == ANNOTATION_METHOD) return new GrAnnotationMethodImpl(node);
    if (elem == ANONYMOUS_TYPE_DEFINITION) return new GrAnonymousClassDefinitionImpl(node);

    if (elem == CODE_REFERENCE) return new GrCodeReferenceElementImpl(node);

    //clauses
    if (elem == EXTENDS_CLAUSE) return new GrExtendsClauseImpl(node);
    if (elem == IMPLEMENTS_CLAUSE) return new GrImplementsClauseImpl(node);
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
    if (elem == XOR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == BOR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == BAND_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == REGEX_MATCH_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == REGEX_FIND_EXPRESSION) return new GrRegexFindExpressionImpl(node);
    if (elem == EQUALITY_EXPRESSION) return new GrRelationalExpressionImpl(node);
    if (elem == RELATIONAL_EXPRESSION) return new GrRelationalExpressionImpl(node);
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

    //Paths
    if (elem == REFERENCE_EXPRESSION) return new GrReferenceExpressionImpl(node);
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
