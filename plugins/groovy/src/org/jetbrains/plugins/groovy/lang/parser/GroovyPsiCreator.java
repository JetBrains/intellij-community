package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrIdentifierImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrMapImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrTypeCastImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifiersImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrParameterModifiersImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments.GrArgumentsImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrConstructorBodyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrMethodBodyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrIndexPropertyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrMethodCallImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrPropertySelectionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrPropertySelectorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrRelationalExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterListImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnnotationTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrInterfaceDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.auxilary.GrBalancedBracketsImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.blocks.GrAnnotationBodyImplType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.blocks.GrClassBodyImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.blocks.GrEnumBodyImplType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.blocks.GrInterfaceBodyImplType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrAnnotationMemberImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrClassMemberImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrEnumMemberImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrInterfaceMemberImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportReferenceImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportSelectorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrArrayTypeElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrBuiltInTypeImpl;

/**
 * Creates Groovy PSI element by given AST node
 *
 * @author Ilya.Sergey, Dmitry.Krasilschikov
 */
public abstract class GroovyPsiCreator implements GroovyElementTypes {

  /**
   * Creates Groovy PSI element by given AST node
   *
   * @param node Given node
   * @return Respective PSI element
   */
  public static PsiElement createElement(ASTNode node) {
    IElementType elem = node.getElementType();

    //Identifiers & literal
    if (elem.equals(IDENTIFIER)) return new GrIdentifierImpl(node);
    if (elem.equals(LITERAL)) return new GrLiteralImpl(node);

    //Lists, mapetc...
    if (elem.equals(LIST)) return new GrListImpl(node);
    if (elem.equals(MAP)) return new GrMapImpl(node);

    if (elem.equals(MODIFIER)) return new GrModifierImpl(node);
    if (elem.equals(MODIFIERS)) return new GrModifiersImpl(node);

    // Imports
    if (elem.equals(IMPORT_STATEMENT)) return new GrImportStatementImpl(node);
    if (elem.equals(IMPORT_SELECTOR)) return new GrImportSelectorImpl(node);
    if (elem.equals(IMPORT_REFERENCE)) return new GrImportReferenceImpl(node);

    // Packaging
    if (elem.equals(PACKAGE_DEFINITION)) return new GrPackageDefinitionImpl(node);

    //statements
    if (elem.equals(IF_STATEMENT)) return new GrIfStatementImpl(node);
    if (elem.equals(FOR_STATEMENT)) return new GrForStatementImpl(node);
    if (elem.equals(WHILE_STATEMENT)) return new GrWhileStatementImpl(node);
    if (elem.equals(WITH_STATEMENT)) return new GrWithStatementImpl(node);
    if (elem.equals(STAR_STATEMENT)) return new GrStarStatementImpl(node);

    //type definitions
    if (elem.equals(CLASS_DEFINITION)) return new GrClassDefinitionImpl(node);
    if (elem.equals(INTERFACE_DEFINITION)) return new GrInterfaceDefinitionImpl(node);
    if (elem.equals(ENUM_DEFINITION)) return new GrEnumTypeDefinitionImpl(node);
    if (elem.equals(ANNOTATION_DEFINITION)) return new GrAnnotationTypeDefinitionImpl(node);

    //blocks
    if (elem.equals(CLASS_BLOCK)) return new GrClassBodyImpl(node);
    if (elem.equals(INTERFACE_BLOCK)) return new GrInterfaceBodyImplType(node);
    if (elem.equals(ENUM_BLOCK)) return new GrEnumBodyImplType(node);
    if (elem.equals(ANNOTATION_BLOCK)) return new GrAnnotationBodyImplType(node);
    if (elem.equals(CLOSABLE_BLOCK)) return new GrClosableBlockImpl(node);
    if (elem.equals(CONSTRUCTOR_BODY)) return new GrConstructorBodyImpl(node);
    if (elem.equals(METHOD_BODY)) return new GrMethodBodyImpl(node);

    //members
    if (elem.equals(CLASS_FIELD)) return new GrClassMemberImpl(node);
    if (elem.equals(INTERFACE_FIELD)) return new GrInterfaceMemberImpl(node);
    if (elem.equals(ENUM_FIELD)) return new GrEnumMemberImpl(node);
    if (elem.equals(ANNOTATION_FIELD)) return new GrAnnotationMemberImpl(node);


    //parameters
    if (elem.equals(PARAMETERS_LIST)) return new GrParameterListImpl(node);
    if (elem.equals(PARAMETER)) return new GrParameterImpl(node);
    if (elem.equals(PARAMETER_MODIFIERS)) return new GrParameterModifiersImpl(node);

    //expressions
    if (elem.equals(EXPRESSION_STATEMENT)) return new GrCallExpressionImpl(node);
    if (elem.equals(COMMAND_ARGUMENTS)) return new GrCommandArgsImpl(node);
    if (elem.equals(CONDITIONAL_EXPRESSION)) return new GrConditionalExprImpl(node);
    if (elem.equals(ASSIGNMENT_EXPRESSION)) return new GrAssignmentExprImpl(node);
    if (elem.equals(LOGICAL_OR_EXPRESSION)) return new GrLogicalOrExprImpl(node);
    if (elem.equals(LOGICAL_AND_EXPRESSION)) return new GrLogicalAndExprImpl(node);
    if (elem.equals(EXCLUSIVE_OR_EXPRESSION)) return new GrExclusiveOrExprImpl(node);
    if (elem.equals(INCLUSIVE_OR_EXPRESSION)) return new GrInclusiveOrExprImpl(node);
    if (elem.equals(AND_EXPRESSION)) return new GrAndExprImpl(node);
    if (elem.equals(REGEX_EXPRESSION)) return new GrRegexExprImpl(node);
    if (elem.equals(EQUALITY_EXPRESSION)) return new GrEqualityExprImpl(node);
    if (elem.equals(RELATIONAL_EXPRESSION)) return new GrRelationalExprImpl(node);
    if (elem.equals(SHIFT_EXPRESSION)) return new GrShiftExprImpl(node);
    if (elem.equals(ADDITIVE_EXPRESSION)) return new GrAdditiveExprImpl(node);
    if (elem.equals(MULTIPLICATIVE_EXPRESSION)) return new GrMultiplicativeExprImpl(node);
    if (elem.equals(POWER_EXPRESSION)) return new GrPowerExprImpl(node);
    if (elem.equals(POWER_EXPRESSION_SIMPLE)) return new GrPowerExprImpl(node);
    if (elem.equals(UNARY_EXPRESSION)) return new GrUnaryExprImpl(node);
    if (elem.equals(POSTFIX_EXPRESSION)) return new GrPostfixExprImpl(node);
    if (elem.equals(CAST_EXPRESSION)) return new GrTypeCastExprImpl(node);
    if (elem.equals(TYPE_CAST)) return new GrTypeCastImpl(node);
    if (elem.equals(ARRAY_TYPE)) return new GrArrayTypeElementImpl(node);
    if (elem.equals(BUILT_IN_TYPE)) return new GrBuiltInTypeImpl(node);
    if (elem.equals(GSTRING)) return new GrStringImpl(node);
    if (elem.equals(REFERENCE_EXPRESSION)) return new GrReferenceExprImpl(node);

    //Paths
    if (elem.equals(PATH_PROPERTY)) return new GrPropertySelectorImpl(node);
    if (elem.equals(PATH_PROPERTY_REFERENCE)) return new GrPropertySelectionImpl(node);
    if (elem.equals(PATH_METHOD_CALL)) return new GrMethodCallImpl(node);
    if (elem.equals(PATH_INDEX_PROPERTY)) return new GrIndexPropertyImpl(node);

    // Arguments
    if (elem.equals(ARGUMENTS)) return new GrArgumentsImpl(node);


    if (elem.equals(BALANCED_BRACKETS)) return new GrBalancedBracketsImpl(node);
    if (elem.equals(DECLARATION)) return new GrDeclarationStatementImpl(node);

    return new ASTWrapperPsiElement(node);
  }

}
