/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.ArrayList;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrReferenceListImpl extends GrStubElementBase<GrReferenceListStub> implements StubBasedPsiElement<GrReferenceListStub>, GrReferenceList {
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

      super.deleteChildInternal(child);
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  @Override
  @Nullable
  public PsiElement getKeyword() {
    PsiElement firstChild = getFirstChild();
    if (firstChild != null && firstChild.getNode().getElementType() == getKeywordType()) {
      return firstChild;
    }
    return null;
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  public GrReferenceListImpl(final GrReferenceListStub stub, IStubElementType elementType) {
    super(stub, elementType);
  }

  @Override
  @NotNull
  public GrCodeReferenceElement[] getReferenceElementsGroovy() {
    final GrReferenceListStub stub = getStub();
    if (stub != null) {
      final String[] baseClasses = stub.getBaseClasses();
      final GrCodeReferenceElement[] result = new GrCodeReferenceElement[baseClasses.length];
      for (int i = 0; i < baseClasses.length; i++) {
        result[i] = GroovyPsiElementFactory.getInstance(getProject()).createReferenceElementFromText(baseClasses[i], this);
      }
      return result;
    }

    return findChildrenByClass(GrCodeReferenceElement.class);
  }

  @NotNull
  @Override
  public PsiClassType[] getReferencedTypes() {
    if (myCachedTypes == null || !isValid()) {
      final ArrayList<PsiClassType> types = new ArrayList<>();
      for (GrCodeReferenceElement ref : getReferenceElementsGroovy()) {
        types.add(new GrClassReferenceType(ref));
      }
      myCachedTypes = types.toArray(new PsiClassType[types.size()]);
    }
    return myCachedTypes;
  }

  @Override
  public void subtreeChanged() {
    myCachedTypes = null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    //hack for inserting references from java code
    if (element instanceof GrCodeReferenceElement || element instanceof PsiJavaCodeReferenceElement) {
      if (findChildByType(getKeywordType()) == null) {
        getNode().getTreeParent().addLeaf(TokenType.WHITE_SPACE, " ", getNode());
        getNode().addLeaf(getKeywordType(), getKeywordType().toString(), null);
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

  protected abstract IElementType getKeywordType();
}
