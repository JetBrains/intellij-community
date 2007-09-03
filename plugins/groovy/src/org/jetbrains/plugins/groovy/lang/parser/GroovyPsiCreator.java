/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jetbrains.plugins.grails.lang.gsp.parsing.GspGroovyElementTypes;
import org.jetbrains.plugins.grails.lang.gsp.psi.groovy.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrLabelImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrListOrMapImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrThrowsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationNameValuePairImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationNameValuePairsImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrParameterModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentLabelImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrNamedArgumentImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrOpenBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrCaseLabelImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrCaseSectionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrForInClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses.GrTraditionalForClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrTypeCastExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrSafeCastExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrInstanceofExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrRegexImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrIndexPropertyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrMethodCallImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrPropertySelectionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrPropertySelectorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrRelationalExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.auxilary.GrBalancedBracketsImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.bodies.GrTypeDefinitionBodyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrDefaultAnnotationMemberImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.*;

/**
 * Creates Groovy PSI element by given AST node
 *
 * @author ilyas, Dmitry.Krasilschikov
 */
public abstract class GroovyPsiCreator implements GroovyElementTypes, GspGroovyElementTypes {

  /**
   * Creates Groovy PSI element by given AST node
   *
   * @param node Given node
   * @return Respective PSI element
   */
  public static PsiElement createElement(ASTNode node) {
    IElementType elem = node.getElementType();

    //Identifiers & literal
    if (elem.equals(LITERAL)) return new GrLiteralImpl(node);
    if (elem.equals(LABEL)) return new GrLabelImpl(node);

    //Lists, mapetc...
    if (elem.equals(LIST_OR_MAP)) return new GrListOrMapImpl(node);

    if (elem.equals(MODIFIERS)) return new GrModifierListImpl(node);
    if (elem.equals(ANNOTATION)) return new GrAnnotationImpl(node);
    if (elem.equals(ANNOTATION_ARGUMENTS)) return new GrAnnotationArgumentListImpl(node);
    if (elem.equals(ANNOTATION_MEMBER_VALUE_PAIR)) return new GrAnnotationNameValuePairImpl(node);
    if (elem.equals(ANNOTATION_MEMBER_VALUE_PAIRS)) return new GrAnnotationNameValuePairsImpl(node);

    if (elem.equals(DEFAULT_ANNOTATION_VALUE)) return new GrDefaultAnnotationValueImpl(node);

    //throws
    if (elem.equals(THROW_CLAUSE)) return new GrThrowsClauseImpl(node);

    // Imports
    if (elem.equals(IMPORT_STATEMENT)) return new GrImportStatementImpl(node);

    // Packaging
    if (elem.equals(PACKAGE_DEFINITION)) return new GrPackageDefinitionImpl(node);

    //statements
    if (elem.equals(LABELED_STATEMENT)) return new GrLabeledStatementImpl(node);
    if (elem.equals(IF_STATEMENT)) return new GrIfStatementImpl(node);
    if (elem.equals(FOR_STATEMENT)) return new GrForStatementImpl(node);
    if (elem.equals(FOR_IN_CLAUSE)) return new GrForInClauseImpl(node);
    if (elem.equals(FOR_TRADITIONAL_CLAUSE)) return new GrTraditionalForClauseImpl(node);
    if (elem.equals(WHILE_STATEMENT)) return new GrWhileStatementImpl(node);
    if (elem.equals(WITH_STATEMENT)) return new GrWithStatementImpl(node);
    if (elem.equals(TRY_BLOCK_STATEMENT)) return new GrTryCatchStmtImpl(node);
    if (elem.equals(CATCH_CLAUSE)) return new GrCatchClauseImpl(node);
    if (elem.equals(FINALLY_CLAUSE)) return new GrFinallyClauseImpl(node);
    if (elem.equals(SYNCHRONIZED_STATEMENT)) return new GrSynchroStmtImpl(node);
    if (elem.equals(SWITCH_STATEMENT)) return new GrSwitchStatementImpl(node);
    if (elem.equals(CASE_LABEL)) return new GrCaseLabelImpl(node);
    if (elem.equals(CASE_SECTION)) return new GrCaseSectionImpl(node);
    if (elem.equals(VARIABLE_DEFINITION) || elem.equals(VARIABLE_DEFINITION_ERROR))
      return new GrVariableDeclarationImpl(node);
    if (elem.equals(VARIABLE)) return new GrVariableImpl(node);

    if (elem.equals(FIELD)) return new GrFieldImpl(node);

    //type definitions
    if (elem.equals(CLASS_DEFINITION) || elem.equals(CLASS_DEFINITION_ERROR)) return new GrClassDefinitionImpl(node);
    if (elem.equals(INTERFACE_DEFINITION) || elem.equals(INTERFACE_DEFINITION_ERROR))
      return new GrInterfaceDefinitionImpl(node);
    if (elem.equals(ENUM_DEFINITION) || elem.equals(ENUM_DEFINITION_ERROR)) return new GrEnumTypeDefinitionImpl(node);
    if (elem.equals(ANNOTATION_DEFINITION) || elem.equals(ANNOTATION_DEFINITION_ERROR))
      return new GrAnnotationTypeDefinitionImpl(node);
    if (elem.equals(DEFAULT_ANNOTATION_MEMBER)) return new GrDefaultAnnotationMemberImpl(node);

    if (elem.equals(REFERENCE_ELEMENT)) return new GrCodeReferenceElementImpl(node);
    if (elem.equals(CLASS_TYPE_ELEMENT)) return new GrClassTypeElementImpl(node);

    //clauses
    if (elem.equals(IMPLEMENTS_CLAUSE)) return new GrImplementsClauseImpl(node);
    if (elem.equals(EXTENDS_CLAUSE)) return new GrExtendsClauseImpl(node);

    //bodies
    if (elem.equals(CLASS_BODY)) return new GrTypeDefinitionBodyImpl(node);
    if (elem.equals(CLOSABLE_BLOCK)) return new GrClosableBlockImpl(node);
    if (elem.equals(OPEN_BLOCK)) return new GrOpenBlockImpl(node);
    if (elem.equals(BLOCK_STATEMENT)) return new GrBlockStatementImpl(node);
    if (elem.equals(EXPLICIT_CONSTRUCTOR)) return new GrConstructorInvocationImpl(node);

    //enum
    if (elem.equals(ENUM_CONSTANTS)) return new GrEnumConstantListImpl(node);
    if (elem.equals(ENUM_CONSTANT)) return new GrEnumConstantImpl(node);

    //members
    if (elem.equals(CONSTRUCTOR_DEFINITION)) return new GrConstructorDefinitionImpl(node);
    if (elem.equals(METHOD_DEFINITION)) return new GrMethodDefinitionImpl(node);

    //parameters
    if (elem.equals(PARAMETERS_LIST)) return new GrParameterListImpl(node);
    if (elem.equals(PARAMETER)) return new GrParameterImpl(node);
    if (elem.equals(PARAMETER_MODIFIERS)) return new GrParameterModifierListImpl(node);

    //type parameters
    if (elem.equals(TYPE_ARGUMENT)) return new GrWildcardTypeArgumentImpl(node);
    if (elem.equals(TYPE_ARGUMENTS)) return new GrTypeArgumentListImpl(node);

    //Branch statements
    if (elem.equals(RETURN_STATEMENT)) return new GrReturnStmtImpl(node);
    if (elem.equals(THROW_STATEMENT)) return new GrThrowStatementImpl(node);
    if (elem.equals(ASSERT_STATEMENT)) return new GrAssertStatementImpl(node);
    if (elem.equals(BREAK_STATEMENT)) return new GrBreakStatementImpl(node);
    if (elem.equals(CONTINUE_STATEMENT)) return new GrContinueStatementImpl(node);

    //expressions
    if (elem.equals(CALL_EXPRESSION)) return new GrApplicationStatementImpl(node);
    if (elem.equals(COMMAND_ARGUMENTS)) return new GrCommandArgumentListImpl(node);
    if (elem.equals(COMMAND_ARGUMENT)) return new GrCommandArgumentImpl(node);
    if (elem.equals(CONDITIONAL_EXPRESSION)) return new GrConditionalExprImpl(node);
    if (elem.equals(ASSIGNMENT_EXPRESSION)) return new GrAssignmentExpressionImpl(node);
    if (elem.equals(LOGICAL_OR_EXPRESSION)) return new GrLogicalOrExprImpl(node);
    if (elem.equals(LOGICAL_AND_EXPRESSION)) return new GrLogicalAndExprImpl(node);
    if (elem.equals(EXCLUSIVE_OR_EXPRESSION)) return new GrExclusiveOrExprImpl(node);
    if (elem.equals(INCLUSIVE_OR_EXPRESSION)) return new GrInclusiveOrExprImpl(node);
    if (elem.equals(AND_EXPRESSION)) return new GrAndExprImpl(node);
    if (elem.equals(REGEX_EXPRESSION)) return new GrRegexExprImpl(node);
    if (elem.equals(EQUALITY_EXPRESSION)) return new GrEqualityExprImpl(node);
    if (elem.equals(RELATIONAL_EXPRESSION)) return new GrRelationalExpressionImpl(node);
    if (elem.equals(SHIFT_EXPRESSION)) return new GrShiftExprImpl(node);
    if (elem.equals(RANGE_EXPRESSION)) return new GrRangeExprImpl(node);
    if (elem.equals(COMPOSITE_SHIFT_SIGN)) return new GrOperationSignImpl(node);
    if (elem.equals(ADDITIVE_EXPRESSION)) return new GrAdditiveExprImpl(node);
    if (elem.equals(MULTIPLICATIVE_EXPRESSION)) return new GrMultiplicativeExprImpl(node);
    if (elem.equals(POWER_EXPRESSION)) return new GrPowerExprImpl(node);
    if (elem.equals(POWER_EXPRESSION_SIMPLE)) return new GrPowerExprImpl(node);
    if (elem.equals(UNARY_EXPRESSION)) return new GrUnaryExpressionImpl(node);
    if (elem.equals(POSTFIX_EXPRESSION)) return new GrPostfixExprImpl(node);
    if (elem.equals(CAST_EXPRESSION)) return new GrTypeCastExpressionImpl(node);
    if (elem.equals(SAFE_CAST_EXPRESSION)) return new GrSafeCastExpressionImpl(node);
    if (elem.equals(INSTANCEOF_EXPRESSION)) return new GrInstanceofExpressionImpl(node);
    if (elem.equals(ARRAY_TYPE)) return new GrArrayTypeElementImpl(node);
    if (elem.equals(BUILT_IN_TYPE)) return new GrBuiltInTypeElementImpl(node);
    if (elem.equals(GSTRING)) return new GrStringImpl(node);
    if (elem.equals(REGEX)) return new GrRegexImpl(node);
    if (elem.equals(REFERENCE_EXPRESSION)) return new GrReferenceExpressionImpl(node);
    if (elem.equals(THIS_REFERENCE_EXPRESSION)) return new GrThisReferenceExpressionImpl(node);
    if (elem.equals(SUPER_REFERENCE_EXPRESSION)) return new GrSuperReferenceExpressionImpl(node);
    if (elem.equals(PARENTHESIZED_EXPRESSION)) return new GrParenthesizedExprImpl(node);
    if (elem.equals(NEW_EXPRESSION)) return new GrNewExpressionImpl(node);
    if (elem.equals(ARRAY_DECLARATOR)) return new GrArrayDeclarationImpl(node);

    //Paths
    if (elem.equals(PATH_PROPERTY)) return new GrPropertySelectorImpl(node);
    if (elem.equals(PATH_PROPERTY_REFERENCE)) return new GrPropertySelectionImpl(node);
    if (elem.equals(PATH_METHOD_CALL)) return new GrMethodCallImpl(node);
    if (elem.equals(PATH_INDEX_PROPERTY)) return new GrIndexPropertyImpl(node);

    // Arguments
    if (elem.equals(ARGUMENTS)) return new GrArgumentListImpl(node);
    if (elem.equals(ARGUMENT)) return new GrNamedArgumentImpl(node);
    if (elem.equals(ARGUMENT_LABEL)) return new GrArgumentLabelImpl(node);

    if (elem.equals(BALANCED_BRACKETS)) return new GrBalancedBracketsImpl(node);

    // GSP-specific Element types
    if (GROOVY_EXPR_CODE.equals(elem)) return new GrGspExprInjectionImpl();
    if (GROOVY_DECLARATION.equals(elem)) return new GrGspDeclarationHolderImpl();

    if (GSP_CLASS.equals(elem)) return new GrGspClassImpl(node);
    if (GSP_RUN_METHOD.equals(elem)) return new GrGspRunMethodImpl(node);
    if (GSP_RUN_BLOCK.equals(elem)) return new GrGspRunBlockImpl(node);


    return new ASTWrapperPsiElement(node);
  }

}
