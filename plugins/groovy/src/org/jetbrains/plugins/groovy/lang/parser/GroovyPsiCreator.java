package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportSelector;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportEnd;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportQualId;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrIdentifier;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrAdditiveExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.AdditiveExpression;
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
    if (elem.equals(IDENTIFIER)) return new GrIdentifier(node);

    // Imports
    if (elem.equals(IMPORT_STATEMENT)) return new GrImportStatement(node);
    if (elem.equals(IMPORT_SELECTOR)) return new GrImportSelector(node);
    if (elem.equals(IMPORT_END)) return new GrImportEnd(node);
    if (elem.equals(IDENITFIER_STAR)) return new GrImportQualId(node);

    // Packaging
    if (elem.equals(PACKAGE_DEFINITION)) return new GrPackageDefinition(node);

    //statements
    if (elem.equals(ADDITIVE_EXXPRESSION)) return new GrAdditiveExpression(node);
    if (elem.equals(ASSIGNMENT_EXXPRESSION)) return new GrAssignmentExpression(node);


//    if (elem.equals(DECLARATION)) return new GrDeclarationImpl(node);
//    if (elem.equals(BALANCED_BRACKETS)) return new GrBalancedBracketsImpl(node);

    return new ASTWrapperPsiElement(node);
  }

}
