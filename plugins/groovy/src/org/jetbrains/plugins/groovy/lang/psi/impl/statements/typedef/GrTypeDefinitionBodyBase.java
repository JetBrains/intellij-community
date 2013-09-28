/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 */
public abstract class GrTypeDefinitionBodyBase extends GrStubElementBase<EmptyStub> implements GrTypeDefinitionBody {
  private GrField[] myFields = null;

  public GrTypeDefinitionBodyBase(@NotNull ASTNode node) {
    super(node);
  }

  public GrTypeDefinitionBodyBase(EmptyStub stub, final IStubElementType classBody) {
    super(stub, classBody);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myFields = null;
    for (GrField field : getFields()) {
      field.clearCaches();
    }
  }

  public abstract void accept(GroovyElementVisitor visitor);

  public String toString() {
    return "Type definition body";
  }

  public GrField[] getFields() {
    if (myFields == null) {
      GrVariableDeclaration[] declarations = getStubOrPsiChildren(GroovyElementTypes.VARIABLE_DEFINITION, GrVariableDeclaration.ARRAY_FACTORY);
      if (declarations.length == 0) return GrField.EMPTY_ARRAY;
      List<GrField> result = new ArrayList<GrField>();
      for (GrVariableDeclaration declaration : declarations) {
        GrVariable[] variables = declaration.getVariables();
        for (GrVariable variable : variables) {
          if (variable instanceof GrField) {
            result.add((GrField) variable);
          }
        }
      }
      myFields = result.toArray(new GrField[result.size()]);
    }

    return myFields;
  }

  public GrMethod[] getMethods() {
    return getStubOrPsiChildren(TokenSets.METHOD_DEFS, GrMethod.ARRAY_FACTORY);
  }

  public GrMembersDeclaration[] getMemberDeclarations() {
    return findChildrenByClass(GrMembersDeclaration.class);
  }

  @Nullable
  public PsiElement getLBrace() {
    return findChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Nullable
  public PsiElement getRBrace() {
    return findChildByType(GroovyTokenTypes.mRCURLY);
  }

  @NotNull
  public GrClassInitializer[] getInitializers() {
    return findChildrenByClass(GrClassInitializer.class);
  }

  public PsiClass[] getInnerClasses() {
    return getStubOrPsiChildren(GroovyElementTypes.TYPE_DEFINITION_TYPES, PsiClass.ARRAY_FACTORY);
  }

  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration, GrStatement anchor) throws IncorrectOperationException {
    PsiElement rBrace = getRBrace();
    if (anchor == null && rBrace == null) {
      throw new IncorrectOperationException();
    }

    if (anchor != null && !this.equals(anchor.getParent())) {
      throw new IncorrectOperationException();
    }

    ASTNode elemNode = declaration.getNode();
    final ASTNode anchorNode = anchor != null ? anchor.getNode() : rBrace.getNode();
    getNode().addChild(elemNode, anchorNode);
    getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchorNode);
    return (GrVariableDeclaration) elemNode.getPsi();
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final PsiElement element = child.getPsi();
    if (element instanceof GrTopStatement) {
      PsiImplUtil.deleteStatementTail(this, element);
    }
    super.deleteChildInternal(child);
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrTopStatement) {
      PsiImplUtil.deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }

  public static class GrClassBody extends GrTypeDefinitionBodyBase implements StubBasedPsiElement<EmptyStub> {

    public GrClassBody(@NotNull ASTNode node) {
      super(node);
    }

    public GrClassBody(EmptyStub stub) {
      super(stub, GroovyElementTypes.CLASS_BODY);
    }

    public void accept(GroovyElementVisitor visitor) {
      visitor.visitTypeDefinitionBody(this);
    }

  }

  public static class GrEnumBody extends GrTypeDefinitionBodyBase implements GrEnumDefinitionBody, StubBasedPsiElement<EmptyStub> {
    public GrEnumBody(@NotNull ASTNode node) {
      super(node);
    }

    public GrEnumBody(EmptyStub stub) {
      super(stub, GroovyElementTypes.ENUM_BODY);
    }

    @Nullable
    public GrEnumConstantList getEnumConstantList() {
      return getStubOrPsiChild(GroovyElementTypes.ENUM_CONSTANTS);
    }

    public void accept(GroovyElementVisitor visitor) {
      visitor.visitEnumDefinitionBody(this);
    }
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    ASTNode afterLast = last.getTreeNext();
    ASTNode next;
    for (ASTNode child = first; child != afterLast; child = next) {
      next = child.getTreeNext();
      if (child.getElementType() == GroovyElementTypes.CONSTRUCTOR_DEFINITION) {
        ASTNode oldIdentifier = child.findChildByType(GroovyTokenTypes.mIDENT);
        ASTNode newIdentifier = ((GrTypeDefinition)getParent()).getNameIdentifierGroovy().getNode().copyElement();
        child.replaceChild(oldIdentifier, newIdentifier);
      }
    }


    return super.addInternal(first, last, anchor, before);
  }
}
