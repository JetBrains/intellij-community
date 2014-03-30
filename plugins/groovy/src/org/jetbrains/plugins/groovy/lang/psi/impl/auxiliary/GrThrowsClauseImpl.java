/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.04.2007
 */
public class GrThrowsClauseImpl extends GroovyPsiElementImpl implements GrThrowsClause {
  public GrThrowsClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitThrowsClause(this);
  }

  public String toString() {
    return "Throw clause";
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    PsiClassType[] types = getReferencedTypes();
    if (types.length == 0) return PsiJavaCodeReferenceElement.EMPTY_ARRAY;

    PsiManagerEx manager = getManager();

    List<PsiJavaCodeReferenceElement> result = ContainerUtil.newArrayList();
    for (PsiClassType type : types) {
      PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
      PsiClass resolved = resolveResult.getElement();
      if (resolved != null) {
        result.add(new LightClassReference(manager, type.getCanonicalText(), resolved, resolveResult.getSubstitutor()));
      }
    }
    return result.toArray(new PsiJavaCodeReferenceElement[result.size()]);
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    List<GrCodeReferenceElement> refs = new ArrayList<GrCodeReferenceElement>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrCodeReferenceElement) refs.add((GrCodeReferenceElement)cur);
    }
    if (refs.size() == 0) return PsiClassType.EMPTY_ARRAY;

    PsiClassType[] result = new PsiClassType[refs.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = new GrClassReferenceType(refs.get(i));
    }

    return result;
  }

  public Role getRole() {
    return Role.THROWS_LIST;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof GrCodeReferenceElement || element instanceof PsiJavaCodeReferenceElement) {
      if (findChildByClass(GrCodeReferenceElement.class) == null) {
        getNode().addLeaf(GroovyTokenTypes.kTHROWS, "throws", null);
      }
      else {
        PsiElement lastChild = getLastChild();
        lastChild = PsiUtil.skipWhitespacesAndComments(lastChild, false);
        if (!lastChild.getNode().getElementType().equals(GroovyTokenTypes.mCOMMA)) {
          getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", null);
        }
      }

      if (element instanceof PsiJavaCodeReferenceElement) {
        element = GroovyPsiElementFactory.getInstance(getProject()).createCodeReferenceElementFromText(element.getText());
      }
    }
    return super.add(element);
  }

}
