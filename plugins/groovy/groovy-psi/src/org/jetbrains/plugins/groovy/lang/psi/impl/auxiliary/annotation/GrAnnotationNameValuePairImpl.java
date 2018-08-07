// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrNameValuePairStub;

import java.lang.ref.Reference;

public class GrAnnotationNameValuePairImpl extends GrStubElementBase<GrNameValuePairStub>
  implements GrAnnotationNameValuePair, StubBasedPsiElement<GrNameValuePairStub> {

  public GrAnnotationNameValuePairImpl(@NotNull GrNameValuePairStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION_MEMBER_VALUE_PAIR);
  }

  public GrAnnotationNameValuePairImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAnnotationNameValuePair(this);
  }

  public String toString() {
    return "Annotation member value pair";
  }

  @Override
  @Nullable
  public String getName() {
    GrNameValuePairStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    final PsiElement nameId = getNameIdentifierGroovy();
    return nameId != null ? nameId.getText() : null;
  }

  @Override
  public String getLiteralValue() {
    return null;
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifierGroovy() {
    PsiElement child = getFirstChild();
    if (child == null) return null;

    IElementType type = child.getNode().getElementType();
    if (TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(type)) return child;

    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  private volatile Reference<PsiAnnotationMemberValue> myDetachedValue;

  @Override
  @Nullable
  public PsiAnnotationMemberValue getDetachedValue() {
    GrNameValuePairStub stub = getStub();
    if (stub != null) {
      String text = stub.getValue();
      PsiAnnotationMemberValue result = SoftReference.dereference(myDetachedValue);
      if (result == null) {
        GrAnnotation annotation = GroovyPsiElementFactory.getInstance(getProject()).createAnnotationFromText(
          "@F(" + text + ")", this
        );
        ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
        PsiAnnotationMemberValue value = annotation.findAttributeValue(null);
        myDetachedValue = new SoftReference<>(result = value);
      }
      return result;
    }

    return getValue();
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myDetachedValue = null;
  }

  @Override
  public GrAnnotationMemberValue getValue() {
    return findChildByClass(GrAnnotationMemberValue.class);
  }

  @Override
  @NotNull
  public PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue) {
    GrAnnotationMemberValue value = getValue();
    if (value == null) {
      return (PsiAnnotationMemberValue)add(newValue);
    }
    else {
      return (PsiAnnotationMemberValue)value.replace(newValue);
    }
  }

  @Override
  public PsiReference getReference() {
    return getNameIdentifierGroovy() == null ? null : new GrAnnotationMethodReference(this);
  }
}
