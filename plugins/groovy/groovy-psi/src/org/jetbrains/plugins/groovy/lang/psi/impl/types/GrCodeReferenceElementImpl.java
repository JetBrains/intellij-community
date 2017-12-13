/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.CodeReferenceKind;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrCodeReferencePolyVariantResolver;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.doGetKind;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrCodeReferenceElementImpl extends GrReferenceElementImpl<GrCodeReferenceElement> implements GrCodeReferenceElement {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.types.GrCodeReferenceElementImpl");

  public GrCodeReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (StringUtil.isJavaIdentifier(newElementName)) {
      return super.handleElementRename(newElementName);
    }
    else {
      throw new IncorrectOperationException("Cannot rename reference to '" + newElementName + "'");
    }
  }

  @Override
  protected GrCodeReferenceElement bindWithQualifiedRef(@NotNull String qName) {
    final GrCodeReferenceElement qualifiedRef = GroovyPsiElementFactory.getInstance(getProject()).createTypeOrPackageReference(qName);
    final PsiElement list = getTypeArgumentList();
    if (list != null) {
      qualifiedRef.getNode().addChild(list.copy().getNode());
    }
    getNode().getTreeParent().replaceChild(getNode(), qualifiedRef.getNode());
    return qualifiedRef;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitCodeReferenceElement(this);
  }

  public String toString() {
    return "Reference element";
  }

  @Override
  public GrCodeReferenceElement getQualifier() {
    return (GrCodeReferenceElement)findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
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
  public boolean isReferenceTo(PsiElement element) {
    final PsiManager manager = getManager();
    if (element instanceof PsiNamedElement && getParent() instanceof GrImportStatement) {
      final GroovyResolveResult[] results = multiResolve(false);
      for (GroovyResolveResult result : results) {
        if (manager.areElementsEquivalent(result.getElement(), element)) return true;
      }
    }
    return manager.areElementsEquivalent(element, resolve());
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, GrCodeReferencePolyVariantResolver.INSTANCE);
  }

  @NotNull
  @Override
  public PsiType[] getTypeArguments() {
    GrTypeArgumentList typeArgumentList = getTypeArgumentList();
    if (typeArgumentList != null && typeArgumentList.isDiamond()) {
      return inferDiamondTypeArguments();
    }
    else {
      return super.getTypeArguments();
    }
  }

  private PsiType[] inferDiamondTypeArguments() {
    PsiElement parent = getParent();
    if (!(parent instanceof GrNewExpression)) return PsiType.EMPTY_ARRAY;

    PsiType lType = PsiImplUtil.inferExpectedTypeForDiamond((GrNewExpression)parent);

    if (lType instanceof PsiClassType) {
      return ((PsiClassType)lType).getParameters();
    }

    return PsiType.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public CodeReferenceKind getKind() {
    return doGetKind(this);
  }
}
