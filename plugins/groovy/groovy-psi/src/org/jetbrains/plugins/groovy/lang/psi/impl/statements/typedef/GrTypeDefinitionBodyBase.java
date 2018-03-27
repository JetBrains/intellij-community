/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.List;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 */
public abstract class GrTypeDefinitionBodyBase extends GrStubElementBase<EmptyStub> implements GrTypeDefinitionBody {
  public GrTypeDefinitionBodyBase(@NotNull ASTNode node) {
    super(node);
  }

  public GrTypeDefinitionBodyBase(EmptyStub stub, final IStubElementType classBody) {
    super(stub, classBody);
  }

  @Override
  public abstract void accept(GroovyElementVisitor visitor);

  public String toString() {
    return "Type definition body";
  }

  @NotNull
  @Override
  public GrField[] getFields() {
    GrVariableDeclaration[] declarations = getStubOrPsiChildren(GroovyElementTypes.VARIABLE_DEFINITION, GrVariableDeclaration.ARRAY_FACTORY);
    List<GrField> result = ContainerUtil.newArrayList();
    for (GrVariableDeclaration declaration : declarations) {
      GrVariable[] variables = declaration.getVariables();
      for (GrVariable variable : variables) {
        if (variable instanceof GrField) {
          result.add((GrField)variable);
        }
      }
    }

    return result.toArray(new GrField[result.size()]);
  }

  @NotNull
  @Override
  public GrMethod[] getMethods() {
    return getStubOrPsiChildren(TokenSets.METHOD_DEFS, GrMethod.ARRAY_FACTORY);
  }

  @NotNull
  @Override
  public GrMembersDeclaration[] getMemberDeclarations() {
    return findChildrenByClass(GrMembersDeclaration.class);
  }

  @Override
  @Nullable
  public PsiElement getLBrace() {
    return findChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Override
  @Nullable
  public PsiElement getRBrace() {
    return findChildByType(GroovyTokenTypes.mRCURLY);
  }

  @Override
  @NotNull
  public GrClassInitializer[] getInitializers() {
    return findChildrenByClass(GrClassInitializer.class);
  }

  @NotNull
  @Override
  public GrTypeDefinition[] getInnerClasses() {
    return getStubOrPsiChildren(TokenSets.TYPE_DEFINITIONS, GrTypeDefinition.ARRAY_FACTORY);
  }

  @Override
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
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

    @Override
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

    @Override
    @Nullable
    public GrEnumConstantList getEnumConstantList() {
      return getStubOrPsiChild(GroovyElementTypes.ENUM_CONSTANTS);
    }

    @NotNull
    @Override
    public GrEnumConstant[] getEnumConstants() {
      GrEnumConstantList list = getEnumConstantList();
      if (list != null) return list.getEnumConstants();
      return GrEnumConstant.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public GrField[] getFields() {
      GrField[] bodyFields = super.getFields();
      GrEnumConstant[] enumConstants = getEnumConstants();
      if (bodyFields.length == 0) return enumConstants;
      if (enumConstants.length == 0) return bodyFields;
      return ArrayUtil.mergeArrays(bodyFields, enumConstants);
    }

    @Override
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
