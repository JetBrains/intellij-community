// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocParameterReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterListOwner;
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult;

import java.util.ArrayList;

public class GrDocParameterReferenceImpl extends GroovyDocPsiElementImpl implements GrDocParameterReference {

  public GrDocParameterReferenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "GrDocParameterReference";
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final String name = getName();
    if (name == null) return ResolveResult.EMPTY_ARRAY;
    ArrayList<GroovyResolveResult> candidates = new ArrayList<>();

    final PsiElement owner = GrDocCommentUtil.findDocOwner(this);
    if (owner instanceof GrMethod method && !name.startsWith("<")) {
      final GrParameter[] parameters = method.getParameters();

      for (GrParameter parameter : parameters) {
        if (name.equals(parameter.getName())) {
          candidates.add(new ElementResolveResult<>(parameter));
        }
      }
      return candidates.toArray(ResolveResult.EMPTY_ARRAY);
    }
    String typeParameterName = name.length() >= 3 && name.startsWith("<") && name.endsWith(">") ? name.substring(1, name.length() - 1) : name;
    final PsiElement firstChild = getFirstChild();
    if (owner instanceof GrTypeParameterListOwner && firstChild != null) {
      final ASTNode node = firstChild.getNode();
      if (node != null) {
        final PsiTypeParameter[] typeParameters = ((PsiTypeParameterListOwner)owner).getTypeParameters();
        for (PsiTypeParameter typeParameter : typeParameters) {
          if (typeParameterName.equals(typeParameter.getName())) {
            candidates.add(new ElementResolveResult<>(typeParameter));
          }
        }
      }
    }
    return candidates.toArray(ResolveResult.EMPTY_ARRAY);
  }

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public String getName() {
    return getText();
  }

  @Override
  public @Nullable PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    if (results.length != 1) return null;
    return results[0].getElement();
  }

  @Override
  public @NotNull String getCanonicalText() {
    return getName();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    ASTNode node = getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createDocMemberReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null;
    node.getTreeParent().replaceChild(node, newNameNode);
    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return this;
    return null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!(element instanceof GrParameter || element instanceof GrTypeParameter)) return false;
    return getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public Object @NotNull [] getVariants() {
    final PsiElement owner = GrDocCommentUtil.findDocOwner(this);
    final PsiElement firstChild = getFirstChild();
    if (owner instanceof GrTypeParameterListOwner && firstChild != null) {
      final ASTNode node = firstChild.getNode();
      if (node != null && node.getText().startsWith("<")) {
        return ((PsiTypeParameterListOwner)owner).getTypeParameters();
      }
    }
    if (owner instanceof PsiMethod) {
      return ((PsiMethod)owner).getParameterList().getParameters();
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public @NotNull GrDocTagValueToken getReferenceNameElement() {
    GrDocTagValueToken token = findChildByClass(GrDocTagValueToken.class);
    assert token != null;
    return token;
  }
}
