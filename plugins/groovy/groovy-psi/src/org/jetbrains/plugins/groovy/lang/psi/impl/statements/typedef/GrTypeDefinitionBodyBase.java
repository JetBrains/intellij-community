// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry.Krasilschikov
 */
public abstract class GrTypeDefinitionBodyBase extends GrStubElementBase<EmptyStub> implements GrTypeDefinitionBody {
  public GrTypeDefinitionBodyBase(@NotNull ASTNode node) {
    super(node);
  }

  public GrTypeDefinitionBodyBase(EmptyStub stub, final IStubElementType classBody) {
    super(stub, classBody);
  }

  @Override
  public abstract void accept(@NotNull GroovyElementVisitor visitor);

  @Override
  public String toString() {
    return "Type definition body";
  }

  @Override
  public GrField @NotNull [] getFields() {
    GrVariableDeclaration[] declarations = getStubOrPsiChildren(GroovyStubElementTypes.VARIABLE_DECLARATION, GrVariableDeclaration.ARRAY_FACTORY);
    List<GrField> result = new ArrayList<>();
    for (GrVariableDeclaration declaration : declarations) {
      GrVariable[] variables = declaration.getVariables();
      for (GrVariable variable : variables) {
        if (variable instanceof GrField) {
          result.add((GrField)variable);
        }
      }
    }

    return result.toArray(GrField.EMPTY_ARRAY);
  }

  @Override
  public GrMethod @NotNull [] getMethods() {
    return getStubOrPsiChildren(TokenSets.METHOD_DEFS, GrMethod.ARRAY_FACTORY);
  }

  @Override
  public GrMembersDeclaration @NotNull [] getMemberDeclarations() {
    return findChildrenByClass(GrMembersDeclaration.class);
  }

  @Override
  public @Nullable PsiElement getLBrace() {
    return findChildByType(GroovyTokenTypes.mLCURLY);
  }

  @Override
  public @Nullable PsiElement getRBrace() {
    return findChildByType(GroovyTokenTypes.mRCURLY);
  }

  @Override
  public GrClassInitializer @NotNull [] getInitializers() {
    return findChildrenByClass(GrClassInitializer.class);
  }

  @Override
  public GrTypeDefinition @NotNull [] getInnerClasses() {
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
      super(stub, GroovyEmptyStubElementTypes.CLASS_BODY);
    }

    @Override
    public void accept(@NotNull GroovyElementVisitor visitor) {
      visitor.visitTypeDefinitionBody(this);
    }

  }

  public static class GrEnumBody extends GrTypeDefinitionBodyBase implements GrEnumDefinitionBody, StubBasedPsiElement<EmptyStub> {
    public GrEnumBody(@NotNull ASTNode node) {
      super(node);
    }

    public GrEnumBody(EmptyStub stub) {
      super(stub, GroovyEmptyStubElementTypes.ENUM_BODY);
    }

    @Override
    public @Nullable GrEnumConstantList getEnumConstantList() {
      return getStubOrPsiChild(GroovyEmptyStubElementTypes.ENUM_CONSTANTS);
    }

    @Override
    public GrEnumConstant @NotNull [] getEnumConstants() {
      GrEnumConstantList list = getEnumConstantList();
      if (list != null) return list.getEnumConstants();
      return GrEnumConstant.EMPTY_ARRAY;
    }

    @Override
    public GrField @NotNull [] getFields() {
      GrField[] bodyFields = super.getFields();
      GrEnumConstant[] enumConstants = getEnumConstants();
      if (bodyFields.length == 0) return enumConstants;
      if (enumConstants.length == 0) return bodyFields;
      return ArrayUtil.mergeArrays(bodyFields, enumConstants);
    }

    @Override
    public void accept(@NotNull GroovyElementVisitor visitor) {
      visitor.visitEnumDefinitionBody(this);
    }
  }

  @Override
  public ASTNode addInternal(@NotNull ASTNode first, @NotNull ASTNode last, ASTNode anchor, Boolean before) {
    ASTNode afterLast = last.getTreeNext();
    ASTNode next;
    for (ASTNode child = first; child != afterLast; child = next) {
      next = child.getTreeNext();
      if (child.getElementType() == GroovyStubElementTypes.CONSTRUCTOR) {
        ASTNode oldIdentifier = child.findChildByType(GroovyTokenTypes.mIDENT);
        ASTNode newIdentifier = ((GrTypeDefinition)getParent()).getNameIdentifierGroovy().getNode().copyElement();
        child.replaceChild(oldIdentifier, newIdentifier);
      }
    }


    return super.addInternal(first, last, anchor, before);
  }
}
