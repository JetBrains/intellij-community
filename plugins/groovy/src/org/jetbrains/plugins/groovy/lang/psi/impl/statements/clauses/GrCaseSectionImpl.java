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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ven
 */
public class GrCaseSectionImpl extends GroovyPsiElementImpl implements GrCaseSection {
  public GrCaseSectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitCaseSection(this);
  }

  public String toString() {
    return "Case section";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, substitutor, lastParent, place);
  }

  public void removeVariable(GrVariable variable) throws IncorrectOperationException {
    PsiImplUtil.removeVariable(variable);
  }

  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration) statement);
  }

  public GrCaseLabel getCaseLabel() {
    return findChildByClass(GrCaseLabel.class);
  }

  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  public GrStatement addStatementBefore(@NotNull GrStatement element, @NotNull GrStatement anchor) throws IncorrectOperationException {

    if (anchor != null && !this.equals(anchor.getParent())) {
      throw new IncorrectOperationException();
    }
    ASTNode elemNode = element.getNode();
    final ASTNode anchorNode = anchor != null ? anchor.getNode() : getLastChild().getNode();
    getNode().addChild(elemNode, anchorNode);
    if (mayUseNewLinesAsSeparators()) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchorNode);
    } else {
      getNode().addLeaf(GroovyTokenTypes.mSEMI, ";", anchorNode);
    }
    PsiFile file = anchor.getContainingFile();
    assert file != null;
    PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    Document document = manager.getDocument(file);
    if (document != null) {
      manager.doPostponedOperationsAndUnblockDocument(document);
    }
    CodeStyleManager.getInstance(getProject()).adjustLineIndent(file, getTextRange());

    return (GrStatement) elemNode.getPsi();
  }

  private boolean mayUseNewLinesAsSeparators() {
    PsiElement parent = this;
    while (parent != null) {
      if (parent instanceof GrString) {
        GrString grString = (GrString) parent;
        return !grString.isPlainString();
      }
      parent = parent.getParent();
    }
    return true;
  }


}