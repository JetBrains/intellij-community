package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrIdentifierImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.auxilary.GrBalancedBracketsImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.*;

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
    if (elem.equals(CLASS_DEFINITION)) return new GrClassDefinitionImpl(node);
    if (elem.equals(INTERFACE_DEFINITION)) return new GrInterfaceDefinitionImpl(node);
    if (elem.equals(ENUM_DEFINITION)) return new GrEnumDefinitionImpl(node);
    if (elem.equals(ANNOTATION_DEFINITION)) return new GrAnnotationDefinitionImpl(node);

    //expressions
    if (elem.equals(ASSIGNMENT_EXPRESSION)) return new GrAssignmentExpressionImpl(node);
    if (elem.equals(ADDITIVE_EXXPRESSION)) return new GrAdditiveExpressionImpl(node);
    if (elem.equals(LOGICAL_OR_EXPRESSION)) return new GrLogicalOrExprImpl(node);
    if (elem.equals(LOGICAL_AND_EXPRESSION)) return new GrLogicalAndExprImpl(node);
    if (elem.equals(EXCLUSIVE_OR_EXPRESSION)) return new GrExclusiveOrExprImpl(node);
    if (elem.equals(INCLUSIVE_OR_EXPRESSION)) return new GrInclusiveOrExprImpl(node);
    if (elem.equals(AND_EXPRESSION)) return new GrAndExprImpl(node);
    if (elem.equals(REGEX_EXPRESSION)) return new GrRegexExprImpl(node);
    if (elem.equals(EQUALITY_EXPRESSION)) return new GrEqualityExprImpl(node);


    if (elem.equals(BALANCED_BRACKETS)) return new GrBalancedBracketsImpl(node);
    if (elem.equals(DECLARATION)) return new GrDeclarationImpl(node);

    return new ASTWrapperPsiElement(node);
  }

}
