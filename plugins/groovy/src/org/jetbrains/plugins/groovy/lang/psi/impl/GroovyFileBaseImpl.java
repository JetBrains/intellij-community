/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;

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

  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }

  public GrTypeDefinition[] getTypeDefinitions() {
    return findChildrenByClass(GrTypeDefinition.class);
  }

  public GrTopLevelDefintion[] getTopLevelDefinitions() {
    return findChildrenByClass(GrTopLevelDefintion.class);
  }

  public GrMethod[] getTopLevelMethods() {
    return findChildrenByClass(GrMethod.class);
  }

  public GrVariableDeclaration[] getTopLevelVariableDeclarations() {
    return findChildrenByClass(GrVariableDeclaration.class);
  }

  public GrTopStatement[] getTopStatements() {
    return findChildrenByClass(GrTopStatement.class);
  }

  public boolean importClass(PsiClass aClass) {
    return addImportForClass(aClass) != null;
  }

  public void removeImport(GrImportStatement importStatement) throws IncorrectOperationException {
    PsiElement before = importStatement.getPrevSibling();
    while (before instanceof PsiWhiteSpace || hasElementType(before, GroovyTokenTypes.mNLS)) {
      before = before.getPrevSibling();
    }

    PsiElement rangeStart = importStatement;
    if (before != null && !(before instanceof PsiImportStatement) && before != importStatement.getPrevSibling()) {
      rangeStart = before.getNextSibling();
      final PsiElement el = addBefore(GroovyPsiElementFactory.getInstance(getProject()).createLineTerminator(2), rangeStart);
      rangeStart=el.getNextSibling();
    }

    PsiElement rangeEnd = importStatement;
    while (true) {
      final PsiElement next = rangeEnd.getNextSibling();
      if (!(next instanceof PsiWhiteSpace) && !hasElementType(next, GroovyTokenTypes.mSEMI)) {
        break;
      }
      rangeEnd = next;
    }
    final PsiElement last = hasElementType(rangeEnd.getNextSibling(), GroovyTokenTypes.mNLS) ? rangeEnd.getNextSibling() : rangeEnd;
    if (rangeStart != null && last != null) {
      deleteChildRange(rangeStart, last);
    }
  }

  private static boolean hasElementType(PsiElement next, final IElementType type) {
    if (next == null) {
      return false;
    }
    final ASTNode astNode = next.getNode();
    if (astNode != null && astNode.getElementType() == type) {
      return true;
    }
    return false;
  }

  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element.isValid()) {
        if (element.getParent() != this) throw new IncorrectOperationException();
        deleteChildRange(element, element);
      }
    }
  }

  public GrStatement addStatementBefore(@NotNull GrStatement statement, @Nullable GrStatement anchor) throws IncorrectOperationException {
    final PsiElement result = addBefore(statement, anchor);
    if (anchor != null) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchor.getNode());
    }
    return (GrStatement) result;
  }

  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitFile(this);
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement) child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @NotNull
  public PsiClass[] getClasses() {
    return getTypeDefinitions();
  }

  public void clearCaches() {
    super.clearCaches();
    myControlFlow = null;
  }


  private Instruction[] myControlFlow = null;

  public Instruction[] getControlFlow() {
    if (myControlFlow == null) {
      myControlFlow = new ControlFlowBuilder(getProject()).buildControlFlow(this, null, null);
    }

    return myControlFlow;
  }
}
