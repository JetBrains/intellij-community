/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
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

  private PsiClassType[] cachedTypes = null;

  public GrReferenceListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  public GrReferenceListImpl(final GrReferenceListStub stub, IStubElementType elementType) {
    super(stub, elementType);
  }

  @NotNull
  public GrCodeReferenceElement[] getReferenceElements() {
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
  public PsiClassType[] getReferenceTypes() {
    if (cachedTypes == null || !isValid()) {
      final ArrayList<PsiClassType> types = new ArrayList<PsiClassType>();
      for (GrCodeReferenceElement ref : getReferenceElements()) {
        types.add(new GrClassReferenceType(ref));
      }
      cachedTypes = types.toArray(new PsiClassType[types.size()]);
    }
    return cachedTypes;
  }

  @Override
  public void subtreeChanged() {
    cachedTypes = null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof GrCodeReferenceElement) {
      if (findChildByType(getKeywordType()) == null) {
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
