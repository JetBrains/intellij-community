package org.jetbrains.plugins.groovy.lang.psi;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author ven
 */
public abstract class GroovyElementVisitor {
  public void visitElement(GroovyPsiElement element) {}

  public void visitFile(GroovyFile file) {}

  public void visitPackageDefinition(GrPackageDefinition packageDefinition) {
    visitElement(packageDefinition);
  }

  public void visitStatement(GrStatement statement) {
    visitElement(statement);
  }

  public void visitClosure(GrClosableBlock closure) {
    visitStatement(closure);
  }

  public void visitBlock(GrOpenBlock block) {
    visitElement(block);
  }

  public void visitEnumConstants(GrEnumConstants enumConstantsSection) {
    visitElement(enumConstantsSection);
  }

  public void visitEnumConstant(GrEnumConstant enumConstant) {
    visitElement(enumConstant);
  }

  public void visitImportStatement(GrImportStatement importStatement) {
    visitElement(importStatement);
  }

  public void visitBreakStatement(GrBreakStatement breakStatement) {
    visitStatement(breakStatement);
  }

  public void visitContinueStatement(GrContinueStatement continueStatement) {
    visitStatement(continueStatement);
  }

  public void visitReturnStatement(GrReturnStatement returnStatement) {
    visitStatement(returnStatement);
  }

  public void visitAssertStatement(GrAssertStatement assertStatement) {
    visitStatement(assertStatement);
  }

  public void visitThrowStatement(GrThrowStatement throwStatement) {
    visitStatement(throwStatement);
  }

  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    visitStatement(labeledStatement);
  }


}
