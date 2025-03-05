// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrReferenceListImpl extends GrStubElementBase<GrReferenceListStub>
  implements StubBasedPsiElement<GrReferenceListStub>, GrReferenceList, PsiListLikeElement {

  private static final Logger LOG = Logger.getInstance(GrReferenceListImpl.class);

  private PsiClassType[] myCachedTypes;

  public GrReferenceListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PsiElement psi = child.getPsi();

    if (psi instanceof GrCodeReferenceElement) {

      GrCodeReferenceElement[] refs = getReferenceElementsGroovy();
      if (refs.length == 1) {
        PsiElement keyword = getKeyword();
        LOG.assertTrue(keyword != null);
        keyword.delete();
      }
      else {
        boolean forward = refs[0] == psi;
        PsiElement comma = forward
                           ? PsiUtil.skipWhitespacesAndComments(psi.getNextSibling(), true, true)
                           : PsiUtil.skipWhitespacesAndComments(psi.getPrevSibling(), false, true);
        if (comma != null && comma.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
          comma.delete();
        }
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public @Nullable PsiElement getKeyword() {
    PsiElement firstChild = getFirstChild();
    if (firstChild != null && firstChild.getNode().getElementType() == getKeywordType()) {
      return firstChild;
    }
    return null;
  }

  public GrReferenceListImpl(final GrReferenceListStub stub, IStubElementType elementType) {
    super(stub, elementType);
  }

  @Override
  public GrCodeReferenceElement @NotNull [] getReferenceElementsGroovy() {
    final GrReferenceListStub stub = getStub();
    if (stub != null) {
      final String[] baseClasses = stub.getBaseClasses();
      final GrCodeReferenceElement[] result = new GrCodeReferenceElement[baseClasses.length];
      for (int i = 0; i < baseClasses.length; i++) {
        result[i] = GroovyPsiElementFactory.getInstance(getProject()).createCodeReference(baseClasses[i], this);
      }
      return result;
    }

    return findChildrenByClass(GrCodeReferenceElement.class);
  }

  @Override
  public PsiClassType @NotNull [] getReferencedTypes() {
    PsiClassType[] cachedTypes = myCachedTypes;
    if (cachedTypes == null || !isValid()) {
      GrCodeReferenceElement[] elementsGroovy = getReferenceElementsGroovy();
      cachedTypes = elementsGroovy.length == 0 ? PsiClassType.EMPTY_ARRAY : new PsiClassType[elementsGroovy.length];
      for (int i = 0; i < elementsGroovy.length; i++) {
        GrCodeReferenceElement ref = elementsGroovy[i];
        cachedTypes[i] = new GrClassReferenceType(ref);
      }
      myCachedTypes = cachedTypes;
    }
    return cachedTypes;
  }

  @Override
  public void subtreeChanged() {
    myCachedTypes = null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    //hack for inserting references from java code
    if (element instanceof GrCodeReferenceElement || element instanceof PsiJavaCodeReferenceElement) {
      IElementType keywordType = getKeywordType();
      if (keywordType == null) return super.add(element);
      if (findChildByType(keywordType) == null) {
        getNode().getTreeParent().addLeaf(TokenType.WHITE_SPACE, " ", getNode());
        getNode().addLeaf(keywordType, keywordType.toString(), null);
      }
      else if (findChildByClass(GrCodeReferenceElement.class) != null) {
        PsiElement lastChild = getLastChild();
        lastChild = PsiUtil.skipWhitespacesAndComments(lastChild, false);
        if (!lastChild.getNode().getElementType().equals(GroovyTokenTypes.mCOMMA)) {
          getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", null);
        }
      }
    }
    return super.add(element);
  }

  protected abstract @Nullable IElementType getKeywordType();

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return Arrays.asList(getReferenceElementsGroovy());
  }
}
