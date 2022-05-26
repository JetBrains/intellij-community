// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.CodeReferenceKind;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrCodeReferenceResolver;

import java.util.Collection;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.PropertyUtilKt.getAccessorName;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrCodeReferenceElementImpl extends GrReferenceElementImpl<GrCodeReferenceElement> implements GrCodeReferenceElement {
  private static final Logger LOG = Logger.getInstance(GrCodeReferenceElementImpl.class);

  public GrCodeReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  private volatile String myCachedTextSkipWhiteSpaceAndComments;

  @Override
  public void subtreeChanged() {
    myCachedTextSkipWhiteSpaceAndComments = null;
    super.subtreeChanged();
  }

  private String getTextSkipWhiteSpaceAndComments() {
    String whiteSpaceAndComments = myCachedTextSkipWhiteSpaceAndComments;
    if (whiteSpaceAndComments == null) {
      myCachedTextSkipWhiteSpaceAndComments = whiteSpaceAndComments = PsiImplUtil.getTextSkipWhiteSpaceAndComments(getNode());
    }
    return whiteSpaceAndComments;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    if (StringUtil.isJavaIdentifier(newElementName)) {
      return super.handleElementRename(newElementName);
    }
    else {
      throw new IncorrectOperationException("Cannot rename reference to '" + newElementName + "'");
    }
  }

  @NotNull
  @Override
  protected GrReferenceElement<GrCodeReferenceElement> createQualifiedRef(@NotNull String qName) {
    return GroovyPsiElementFactory.getInstance(getProject()).createCodeReference(qName);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitCodeReferenceElement(this);
  }

  @Override
  public String toString() {
    return "Reference element";
  }

  @Override
  public GrCodeReferenceElement getQualifier() {
    return findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return findChildByType(TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS);
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    switch (getKind()) {
      case PACKAGE_REFERENCE:
      case IMPORT_REFERENCE:
        return getTextSkipWhiteSpaceAndComments();
      case REFERENCE:
        final PsiElement target = resolve();
        if (target instanceof PsiTypeParameter) {
          return StringUtil.notNullize(((PsiTypeParameter)target).getName());
        }
        else if (target instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)target;
          String name = aClass.getQualifiedName();
          if (name == null) return "";

          final PsiType[] types = getTypeArguments();
          if (types.length == 0) return name;

          final StringBuilder buf = new StringBuilder();
          buf.append(name);
          buf.append('<');
          for (int i = 0; i < types.length; i++) {
            if (i > 0) buf.append(',');
            buf.append(types[i].getCanonicalText());
          }
          buf.append('>');

          return buf.toString();
        }
        else if (target instanceof PsiPackage) {
          return ((PsiPackage)target).getQualifiedName();
        }
        else {
          LOG.assertTrue(target == null);
          return getTextSkipWhiteSpaceAndComments();
        }
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  protected boolean bindsCorrectly(PsiElement element) {
    if (super.bindsCorrectly(element)) return true;
    if (element instanceof PsiClass) {
      final PsiElement resolved = resolve();
      if (resolved instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)resolved;
        if (method.isConstructor() && getManager().areElementsEquivalent(element, method.getContainingClass())) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public boolean isFullyQualified() {
    PsiElement resolved = resolve();
    if (resolved instanceof PsiPackage) {
      return true;
    }
    else if (resolved instanceof PsiClass) {
      final String qualifiedReferenceName = getQualifiedReferenceName();
      if (qualifiedReferenceName == null) return false;
      final String classFqn = ((PsiClass)resolved).getQualifiedName();
      if (classFqn == null) return false;
      return qualifiedReferenceName.equals(classFqn);
    }
    else {
      return false;
    }
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    switch (getKind()) {
      case PACKAGE_REFERENCE:
        return referencesPackage(element);
      case REFERENCE:
        return referencesPackage(element) || element instanceof PsiClass && resolvesTo(element);
      case IMPORT_REFERENCE:
        return ((element instanceof PsiClass || element instanceof PsiField) && checkName((PsiNamedElement)element) && resolvesTo(element))
               || (element instanceof PsiMethod && checkPropertyName((PsiNamedElement)element) && multiResolvesTo(element))
               || (element instanceof PsiPackage && referencesPackage(element));
      default:
        throw new IllegalStateException();
    }
  }

  private boolean referencesPackage(@NotNull PsiElement element) {
    return element instanceof PsiPackage && checkName((PsiNamedElement)element) && resolvesTo(element);
  }

  private boolean checkName(@NotNull PsiNamedElement namedElement) {
    final String referenceName = getReferenceName();
    if (referenceName == null) return false;
    final String name = namedElement.getName();
    return referenceName.equals(name);
  }

  private boolean checkPropertyName(@NotNull PsiNamedElement namedElement) {
    final String referenceName = getReferenceName();
    if (referenceName == null) return false;
    final String name = namedElement.getName();
    if (name == null) return false;
    return referenceName.equals(name) || ContainerUtil.or(PropertyKind.values(), kind -> getAccessorName(kind, referenceName).equals(name));
  }

  private boolean resolvesTo(@NotNull PsiElement element) {
    return getManager().areElementsEquivalent(element, resolve());
  }

  private boolean multiResolvesTo(@NotNull PsiElement element) {
    final PsiManager manager = getManager();
    return resolve(false).stream()
      .map(it -> it.getElement())
      .anyMatch(it -> manager.areElementsEquivalent(it, element));
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @NotNull
  @Override
  public Collection<? extends GroovyResolveResult> resolve(boolean incomplete) {
    return TypeInferenceHelper.getTopContext().resolve(this, incomplete, GrCodeReferenceResolver.INSTANCE);
  }

  @Override
  public PsiType @NotNull [] getTypeArguments() {
    if (shouldInferTypeArguments(this)) {
      return getDiamondTypes(this);
    }
    else {
      return super.getTypeArguments();
    }
  }

  @NotNull
  @Override
  public CodeReferenceKind getKind() {
    return doGetKind(this);
  }

  @Override
  public GrAnnotation @NotNull [] getAnnotations() {
    return findChildrenByType(GroovyStubElementTypes.ANNOTATION, GrAnnotation.class);
  }
}
