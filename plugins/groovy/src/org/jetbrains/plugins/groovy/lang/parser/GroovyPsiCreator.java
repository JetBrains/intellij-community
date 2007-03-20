package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrIdentifierImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrTypeCastImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrArrayTypeImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifiersImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrAssignmentExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrConditionalExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrCommandArgsImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrRelationalExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnnotationDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.fields.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.blocks.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.auxilary.GrBalancedBracketsImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportEndImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportQualIdImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportSelectorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;

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

    //Identifiers
    if (elem.equals(IDENTIFIER)) return new GrIdentifierImpl(node);

    if (elem.equals(MODIFIER)) return new GrModifierImpl(node);
    if (elem.equals(MODIFIERS)) return new GrModifiersImpl(node);

    // Imports
    if (elem.equals(IMPORT_STATEMENT)) return new GrImportStatementImpl(node);
    if (elem.equals(IMPORT_SELECTOR)) return new GrImportSelectorImpl(node);
    if (elem.equals(IMPORT_END)) return new GrImportEndImpl(node);
    if (elem.equals(IDENITFIER_STAR)) return new GrImportQualIdImpl(node);

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
    if (elem.equals(ENUM_DEFINITION)) return new GrEnumDefinitionImpl(node);
    if (elem.equals(ANNOTATION_DEFINITION)) return new GrAnnotationDefinitionImpl(node);

    //blocks
    if (elem.equals(CLASS_BLOCK)) return new GrClassBlockImpl(node);
    if (elem.equals(INTERFACE_BLOCK)) return new GrInterfaceBlockImpl(node);
    if (elem.equals(ENUM_BLOCK)) return new GrEnumBlockImpl(node);
    if (elem.equals(ANNOTATION_BLOCK)) return new GrAnnotationBlockImpl(node);

    //fields
    if (elem.equals(CLASS_FIELD)) return new GrClassFieldImpl(node);
    if (elem.equals(INTERFACE_FIELD)) return new GrInterfaceFieldImpl(node);
    if (elem.equals(ENUM_FIELD)) return new GrEnumFieldImpl(node);
    if (elem.equals(ANNOTATION_FIELD)) return new GrAnnotationFieldImpl(node);

    //expressions
    if (elem.equals(EXPRESSION_STATEMENT)) return new GrExpressionImpl(node);
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
    if (elem.equals(UNARY_EXPRESSION_NOT_PM)) return new GrSimpleUnaryExprImpl(node);
    if (elem.equals(TYPE_CAST)) return new GrTypeCastImpl(node);
    if (elem.equals(ARRAY_TYPE)) return new GrArrayTypeImpl(node);


    if (elem.equals(BALANCED_BRACKETS)) return new GrBalancedBracketsImpl(node);
    if (elem.equals(DECLARATION)) return new GrDeclarationImpl(node);

    return new ASTWrapperPsiElement(node);
  }

}
