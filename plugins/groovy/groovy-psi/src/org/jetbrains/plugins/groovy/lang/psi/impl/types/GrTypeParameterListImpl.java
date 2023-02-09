// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.EmptyStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.processElement;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessTypeParameters;

public class GrTypeParameterListImpl extends GrStubElementBase<EmptyStub>
  implements GrTypeParameterList, StubBasedPsiElement<EmptyStub>, PsiListLikeElement {

  public GrTypeParameterListImpl(EmptyStub stub) {
    super(stub, GroovyEmptyStubElementTypes.TYPE_PARAMETER_LIST);
  }

  public GrTypeParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "Type parameter list";
  }

  @Override
  public GrTypeParameter @NotNull [] getTypeParameters() {
    return getStubOrPsiChildren(GroovyStubElementTypes.TYPE_PARAMETER, GrTypeParameter.ARRAY_FACTORY);
  }

  @Override
  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    final GrTypeParameter[] typeParameters = getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      if (typeParameters[i].equals(typeParameter)) return i;
    }

    return -1;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTypeParameterList(this);
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) {
    if (!(element instanceof PsiTypeParameter)) return super.add(element);

    return addInternal(element.getNode(), element.getNode(), null, true).getPsi();

  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    appendParenthesesIfNeeded();

    if (first == last && first.getPsi() instanceof PsiTypeParameter) {
      boolean hasParams = getTypeParameters().length > 0;

      final ASTNode _anchor;

      if (anchor == null) {
        if (before.booleanValue()) {
          _anchor = getLastChild().getNode();
        }
        else {
          _anchor = getFirstChild().getNode();
        }
      }
      else {
        _anchor = anchor;
      }


      final ASTNode node = super.addInternal(first, last, _anchor, before);
      if (hasParams) {
        getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", anchor != null ? anchor : node);
      }
      return node;
    }
    else {
      return super.addInternal(first, last, anchor, before);
    }
  }

  private void appendParenthesesIfNeeded() {
    PsiElement first = getFirstChild();
    if (first == null) {
      getNode().addLeaf(GroovyTokenTypes.mLT, "<", null);
    }

    PsiElement last = getLastChild();
    if (last.getNode().getElementType() != GroovyTokenTypes.mGT) {
      getNode().addLeaf(GroovyTokenTypes.mGT, ">", null);
    }

    PsiElement parent = getParent();
    if (parent instanceof GrMethod) {
      GrModifierList list = ((GrMethod)parent).getModifierList();
      PsiElement[] modifiers = list.getModifiers();
      if (modifiers.length == 0) {
        list.setModifierProperty(GrModifier.DEF, true);
      }
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!shouldProcessTypeParameters(processor)) return true;
    for (GrTypeParameter typeParameter : getTypeParameters()) {
      if (!processElement(processor, typeParameter, state)) return false;
    }
    return true;
  }

  @NotNull
  @Override
  public List<? extends PsiElement> getComponents() {
    return Arrays.asList(getTypeParameters());
  }
}
