/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.IGroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.GroovyDocPsiCreator;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
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
public class GroovyPsiCreator implements GroovyElementTypes {

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
    if (elem == LITERAL) return new GrLiteralImpl(node);
//    if (elem.equals(IDENTIFIER)) return new GrIdentifierImpl(node);
    //Lists, maps etc...
    if (elem == LIST_OR_MAP) return new GrListOrMapImpl(node);

    if (elem == MODIFIERS) return new GrModifierListImpl(node);
    if (elem == ANNOTATION) return new GrAnnotationImpl(node);
    if (elem == ANNOTATION_ARGUMENTS) return new GrAnnotationArgumentListImpl(node);
    if (elem == ANNOTATION_ARRAY_INITIALIZER) return new GrAnnotationArrayInitializerImpl(node);
    if (elem == ANNOTATION_MEMBER_VALUE_PAIR) return new GrAnnotationNameValuePairImpl(node);

    if (elem == DEFAULT_ANNOTATION_VALUE) return new GrDefaultAnnotationValueImpl(node);

    //throws
    if (elem == THROW_CLAUSE) return new GrThrowsClauseImpl(node);

    // Imports
    if (elem == IMPORT_STATEMENT) return new GrImportStatementImpl(node);

    // Packaging
    if (elem == PACKAGE_DEFINITION) return new GrPackageDefinitionImpl(node);

    //statements
    if (elem == LABELED_STATEMENT) return new GrLabeledStatementImpl(node);
    if (elem == IF_STATEMENT) return new GrIfStatementImpl(node);
    if (elem == FOR_STATEMENT) return new GrForStatementImpl(node);
    if (elem == FOR_IN_CLAUSE) return new GrForInClauseImpl(node);
    if (elem == FOR_TRADITIONAL_CLAUSE) return new GrTraditionalForClauseImpl(node);
    if (elem == WHILE_STATEMENT) return new GrWhileStatementImpl(node);
    if (elem == TRY_BLOCK_STATEMENT) return new GrTryCatchStatementImpl(node);
    if (elem == CATCH_CLAUSE) return new GrCatchClauseImpl(node);
    if (elem == FINALLY_CLAUSE) return new GrFinallyClauseImpl(node);
    if (elem == SYNCHRONIZED_STATEMENT) return new GrSynchronizedStatementImpl(node);
    if (elem == SWITCH_STATEMENT) return new GrSwitchStatementImpl(node);
    if (elem == CASE_LABEL) return new GrCaseLabelImpl(node);
    if (elem == CASE_SECTION) return new GrCaseSectionImpl(node);
    if (elem == VARIABLE_DEFINITION || elem == VARIABLE_DEFINITION_ERROR) return new GrVariableDeclarationImpl(node);
    if (elem == TUPLE_EXPRESSION) return new GrTupleExpressionImpl(node);
    if (elem == VARIABLE) return new GrVariableImpl(node);

    if (elem == FIELD) return new GrFieldImpl(node);
    if (elem == CLASS_INITIALIZER) return new GrClassInitializerImpl(node);

    //type definitions
    if (elem == CLASS_DEFINITION) return new GrClassDefinitionImpl(node);
    if (elem == INTERFACE_DEFINITION)
      return new GrInterfaceDefinitionImpl(node);
    if (elem == ENUM_DEFINITION) return new GrEnumTypeDefinitionImpl(node);
    if (elem == ANNOTATION_DEFINITION)
      return new GrAnnotationTypeDefinitionImpl(node);
    if (elem == ANNOTATION_METHOD) return new GrAnnotationMethodImpl(node);

    if (elem == REFERENCE_ELEMENT) return new GrCodeReferenceElementImpl(node);
    if (elem == CLASS_TYPE_ELEMENT) return new GrClassTypeElementImpl(node);

    //clauses
    if (elem == IMPLEMENTS_CLAUSE) return new GrImplementsClauseImpl(node);
    if (elem == EXTENDS_CLAUSE) return new GrExtendsClauseImpl(node);

    //bodies
    if (elem == CLASS_BODY) return new GrTypeDefinitionBodyBase.GrClassBody(node);
    if (elem == ENUM_BODY) return new GrTypeDefinitionBodyBase.GrEnumBody(node);
    if (elem == BLOCK_STATEMENT) return new GrBlockStatementImpl(node);
    if (elem == EXPLICIT_CONSTRUCTOR) return new GrConstructorInvocationImpl(node);

    //enum
    if (elem == ENUM_CONSTANTS) return new GrEnumConstantListImpl(node);
    if (elem == ENUM_CONSTANT) return new GrEnumConstantImpl(node);

    //members
    if (elem == CONSTRUCTOR_DEFINITION) return new GrConstructorImpl(node);
    if (elem == METHOD_DEFINITION) return new GrMethodImpl(node);

    //parameters
    if (elem == PARAMETERS_LIST) return new GrParameterListImpl(node);
    if (elem == PARAMETER) return new GrParameterImpl(node);

    //type parameters
    if (elem == TYPE_ARGUMENT) return new GrWildcardTypeArgumentImpl(node);
    if (elem == TYPE_ARGUMENTS) return new GrTypeArgumentListImpl(node);


    if (elem == TYPE_PARAMETER_LIST) return new GrTypeParameterListImpl(node);
    if (elem == TYPE_PARAMETER) return new GrTypeParameterImpl(node);
    if (elem == TYPE_PARAMETER_EXTENDS_BOUND_LIST) return new GrTypeParameterParameterExtendsListImpl(node);

    //Branch statements
    if (elem == RETURN_STATEMENT) return new GrReturnStatementImpl(node);
    if (elem == THROW_STATEMENT) return new GrThrowStatementImpl(node);
    if (elem == ASSERT_STATEMENT) return new GrAssertStatementImpl(node);
    if (elem == BREAK_STATEMENT) return new GrBreakStatementImpl(node);
    if (elem == CONTINUE_STATEMENT) return new GrContinueStatementImpl(node);

    //expressions
    if (elem == CALL_EXPRESSION) return new GrApplicationStatementImpl(node);
    if (elem == COMMAND_ARGUMENTS) return new GrCommandArgumentListImpl(node);
    if (elem == CONDITIONAL_EXPRESSION) return new GrConditionalExprImpl(node);
    if (elem == ELVIS_EXPRESSION) return new GrElvisExprImpl(node);
    if (elem == ASSIGNMENT_EXPRESSION) return new GrAssignmentExpressionImpl(node);

    if (elem == LOGICAL_OR_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == LOGICAL_AND_EXPRESSION) return new GrLogicalExpressionImpl(node);

    if (elem == EXCLUSIVE_OR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == INCLUSIVE_OR_EXPRESSION) return new GrBitwiseExpressionImpl(node);
    if (elem == AND_EXPRESSION) return new GrBitwiseExpressionImpl(node);

    if (elem == REGEX_MATCH_EXPRESSION) return new GrLogicalExpressionImpl(node);
    if (elem == REGEX_FIND_EXPRESSION) return new GrRegexFindExpressionImpl(node);
    if (elem == EQUALITY_EXPRESSION) return new GrRelationalExpressionImpl(node);
    if (elem == RELATIONAL_EXPRESSION) return new GrRelationalExpressionImpl(node);
    if (elem == SHIFT_EXPRESSION) return new GrShiftExpressionImpl(node);
    if (elem == RANGE_EXPRESSION) return new GrRangeExpressionImpl(node);
    if (TokenSets.SHIFT_SIGNS.contains(elem)) return new GrOperationSignImpl(node);
    if (elem == ADDITIVE_EXPRESSION) return new GrAdditiveExpressionImpl(node);
    if (elem == MULTIPLICATIVE_EXPRESSION) return new GrMultiplicativeExpressionImpl(node);
    if (elem == POWER_EXPRESSION) return new GrPowerExpressionImpl(node);
    if (elem == POWER_EXPRESSION_SIMPLE) return new GrPowerExpressionImpl(node);
    if (elem == UNARY_EXPRESSION) return new GrUnaryExpressionImpl(node);
    if (elem == CAST_EXPRESSION) return new GrTypeCastExpressionImpl(node);
    if (elem == SAFE_CAST_EXPRESSION) return new GrSafeCastExpressionImpl(node);
    if (elem == INSTANCEOF_EXPRESSION) return new GrInstanceofExpressionImpl(node);
    if (elem == BUILT_IN_TYPE_EXPRESSION) return new GrBuiltinTypeClassExpressionImpl(node);
    if (elem == ARRAY_TYPE) return new GrArrayTypeElementImpl(node);
    if (elem == BUILT_IN_TYPE) return new GrBuiltInTypeElementImpl(node);
    if (elem == DISJUNCTION_TYPE_ELEMENT) return new GrDisjunctionTypeElementImpl(node);
    if (elem == GSTRING) return new GrStringImpl(node);
    if (elem == REGEX) return new GrRegexImpl(node);
    if (elem == GSTRING_INJECTION) return new GrStringInjectionImpl(node);
    if (elem == GSTRING_CONTENT) return new GrStringContentImpl(node);
    if (elem == REFERENCE_EXPRESSION) return new GrReferenceExpressionImpl(node);
    if (elem == PARENTHESIZED_EXPRESSION) return new GrParenthesizedExpressionImpl(node);
    if (elem == NEW_EXPRESSION) return new GrNewExpressionImpl(node);
    if (elem == ANONYMOUS_CLASS_DEFINITION) return new GrAnonymousClassDefinitionImpl(node);
    if (elem == ENUM_CONSTANT_INITIALIZER) return new GrEnumConstantInitializerImpl(node);
    if (elem == ARRAY_DECLARATOR) return new GrArrayDeclarationImpl(node);

    //Paths
    if (elem == PATH_PROPERTY_REFERENCE) return new GrPropertySelectionImpl(node);
    if (elem == PATH_METHOD_CALL) return new GrMethodCallExpressionImpl(node);
    if (elem == PATH_INDEX_PROPERTY) return new GrIndexPropertyImpl(node);

    // Arguments
    if (elem == ARGUMENTS) return new GrArgumentListImpl(node);
    if (elem == NAMED_ARGUMENT) return new GrNamedArgumentImpl(node);
    if (elem == SPREAD_ARGUMENT) return new GrSpreadArgumentImpl(node);
    if (elem == ARGUMENT_LABEL) return new GrArgumentLabelImpl(node);

    if (elem == BALANCED_BRACKETS) return new GroovyASTPsiElementImpl(node);
    if (elem == mREGEX_LITERAL || elem == mDOLLAR_SLASH_REGEX_LITERAL) return new GroovyASTPsiElementImpl(node);

    return new ASTWrapperPsiElement(node);
  }

}
