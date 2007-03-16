package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportSelectorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportEndImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportQualIdImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrIdentifierImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrAdditiveExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrAssignmentExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.extapi.psi.ASTWrapperPsiElement;

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
    if (elem.equals(ADDITIVE_EXXPRESSION)) return new GrAdditiveExpressionImpl(node);
    if (elem.equals(LOGICAL_OR_EXPRESSION)) return new GrLogicalOrExprImpl(node);
    if (elem.equals(LOGICAL_AND_EXPRESSION)) return new GrLogicalAndExprImpl(node);
    if (elem.equals(EXCLUSIVE_OR_EXPRESSION)) return new GrExclusiveOrExprImpl(node);
    if (elem.equals(INCLUSIVE_OR_EXPRESSION)) return new GrInclusiveOrExprImpl(node);
    if (elem.equals(AND_EXPRESSION)) return new GrAndExprImpl(node);
    if (elem.equals(REGEX_EXPRESSION)) return new GrRegexExprImpl(node);
    if (elem.equals(EQUALITY_EXPRESSION)) return new GrEqualityExprImpl(node);


//    if (elem.equals(DECLARATION)) return new GrDeclarationImpl(node);
//    if (elem.equals(BALANCED_BRACKETS)) return new GrBalancedBracketsImpl(node);

    return new ASTWrapperPsiElement(node);
  }

}
