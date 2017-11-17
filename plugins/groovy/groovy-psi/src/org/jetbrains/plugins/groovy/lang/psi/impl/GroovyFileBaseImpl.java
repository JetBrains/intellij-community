// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyFileImports;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.processClassesInFile;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.processClassesInPackage;

/**
 * @author ilyas
 */
public abstract class GroovyFileBaseImpl extends PsiFileBase implements GroovyFileBase, GrControlFlowOwner {

  protected GroovyFileBaseImpl(FileViewProvider viewProvider, @NotNull Language language) {
    super(viewProvider, language);
  }

  public GroovyFileBaseImpl(IFileElementType root, IFileElementType root1, FileViewProvider provider) {
    this(provider, root.getLanguage());
    init(root, root1);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }

  @Override
  @NotNull
  public GrTypeDefinition[] getTypeDefinitions() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(TokenSets.TYPE_DEFINITIONS, GrTypeDefinition.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(TokenSets.TYPE_DEFINITIONS, GrTypeDefinition.ARRAY_FACTORY);
  }

  @Override
  @NotNull
  public GrMethod[] getMethods() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyElementTypes.METHOD_DEFINITION, GrMethod.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyElementTypes.METHOD_DEFINITION, GrMethod.ARRAY_FACTORY);
  }

  @Override
  @NotNull
  public GrTopStatement[] getTopStatements() {
    return findChildrenByClass(GrTopStatement.class);
  }

  @Override
  public boolean importClass(PsiClass aClass) {
    return addImportForClass(aClass) != null;
  }

  @Override
  public void removeImport(@NotNull GrImportStatement importStatement) throws IncorrectOperationException {
    GroovyCodeStyleManager.getInstance(getProject()).removeImport(this, importStatement);
  }

  @Override
  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element.isValid()) {
        if (element.getParent() != this) throw new IncorrectOperationException();
        deleteChildRange(element, element);
      }
    }
  }

  @NotNull
  @Override
  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  @Override
  @NotNull
  public GrStatement addStatementBefore(@NotNull GrStatement statement, @Nullable GrStatement anchor) throws IncorrectOperationException {
    final PsiElement result = addBefore(statement, anchor);
    if (anchor != null) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchor.getNode());
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", result.getNode());
    }
    return (GrStatement)result;
  }

  @Override
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @Override
  @NotNull
  public PsiClass[] getClasses() {
    return getTypeDefinitions();
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myControlFlow = null;
  }

  private volatile SoftReference<Instruction[]> myControlFlow;

  @Override
  public Instruction[] getControlFlow() {
    assert isValid();
    Instruction[] result = SoftReference.dereference(myControlFlow);
    if (result == null) {
      result = new ControlFlowBuilder(getProject()).buildControlFlow(this);
      myControlFlow = new SoftReference<>(result);
    }
    return ControlFlowBuilder.assertValidPsi(result);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return false;
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrTopStatement) {
      PsiImplUtil.deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final GroovyFileImports imports = getImports();
    if (!processClassesInFile(this, processor, state)) return false;
    if (!imports.processAllNamedImports(processor, state, place)) return false;
    if (!processClassesInPackage(this, processor, state, place)) return false;
    if (!imports.processAllStarImports(processor, state, place)) return false;
    if (!imports.processDefaultImports(processor, state, place)) return false;
    return true;
  }

  protected abstract GroovyFileImports getImports();
}
