// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrNameValuePairStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult;

import java.lang.ref.Reference;
import java.util.List;

public class GrAnnotationNameValuePairImpl extends GrStubElementBase<GrNameValuePairStub>
  implements GrAnnotationNameValuePair, PsiPolyVariantReference, StubBasedPsiElement<GrNameValuePairStub> {

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
    return getNameIdentifierGroovy() == null ? null : this;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    PsiElement nameId = getNameIdentifierGroovy();
    assert nameId != null;
    return nameId.getTextRange().shiftRight(-getTextRange().getStartOffset());
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    final GroovyResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return getRangeInElement().substring(getText());
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getNameIdentifierGroovy();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null;
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      assert node != null;
      getNode().replaceChild(node, newNameNode);
    } else {
      PsiElement first = getFirstChild();
      ASTNode anchorBefore = first != null ? first.getNode() : null;
      getNode().addLeaf(GroovyTokenTypes.mASSIGN, "=", anchorBefore);
      getNode().addChild(newNameNode, anchorBefore);
    }

    return this;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NYI");
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return element instanceof PsiMethod && getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    GrAnnotation annotation = PsiImplUtil.getAnnotation(this);
    if (annotation != null) {
      GrCodeReferenceElement ref = annotation.getClassReference();
      PsiElement resolved = ref.resolve();

      String declaredName = getName();
      String name = declaredName == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : declaredName;

      if (resolved instanceof PsiClass) {
        final PsiAnnotation collector = GrAnnotationCollector.findAnnotationCollector((PsiClass)resolved);
        if (collector != null) {
          return multiResolveFromAlias(annotation, name, collector);
        }

        if (((PsiClass)resolved).isAnnotationType()) {
          return multiResolveFromAnnotationType((PsiClass)resolved, name);
        }
      }
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private static GroovyResolveResult[] multiResolveFromAnnotationType(@NotNull PsiClass resolved, @NotNull String name) {
    PsiMethod[] methods = resolved.findMethodsByName(name, false);
    if (methods.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

    final GroovyResolveResult[] results = new GroovyResolveResult[methods.length];
    for (int i = 0; i < methods.length; i++) {
      results[i] = new ElementResolveResult<>(methods[i]);
    }
    return results;
  }

  private static GroovyResolveResult[] multiResolveFromAlias(@NotNull GrAnnotation alias, @NotNull String name, @NotNull PsiAnnotation annotationCollector) {
    List<GroovyResolveResult> result = ContainerUtilRt.newArrayList();

    List<GrAnnotation> annotations = ContainerUtilRt.newArrayList();
    GrAnnotationCollector.collectAnnotations(annotations, alias, annotationCollector);

    for (GrAnnotation annotation : annotations) {
      final PsiElement clazz = annotation.getClassReference().resolve();
      if (clazz instanceof PsiClass && ((PsiClass)clazz).isAnnotationType()) {
        if (GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(((PsiClass)clazz).getQualifiedName())) continue;
        for (PsiMethod method : ((PsiClass)clazz).findMethodsByName(name, false)) {
          result.add(new ElementResolveResult<>(method));
        }
      }
    }

    return result.toArray(GroovyResolveResult.EMPTY_ARRAY);
  }
}
