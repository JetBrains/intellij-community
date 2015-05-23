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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public class GrCaseLabelImpl extends GroovyPsiElementImpl implements GrCaseLabel {

  private static final ResolveCache.PolyVariantResolver<GrCaseLabelImpl> RESOLVER =
    new ResolveCache.PolyVariantResolver<GrCaseLabelImpl>() {

      @NotNull
      @Override
      public ResolveResult[] resolve(@NotNull GrCaseLabelImpl label, boolean incompleteCode) {
        final Pair.NonNull<PsiType, PsiType> types = getTypes(label);
        if (types == null) return GroovyResolveResult.EMPTY_ARRAY;
        return ResolveUtil.getMethodCandidates(types.first, "isCase", label, types.second);
      }

      @Nullable
      private Pair.NonNull<PsiType, PsiType> getTypes(@NotNull GrCaseLabelImpl label) {
        final GrExpression caseValue = label.getValue();
        if (caseValue == null) return null;
        final PsiElement parent = label.getParent().getParent();
        if ((!(parent instanceof GrSwitchStatement))) return null;
        final GrExpression switchValue = ((GrSwitchStatement)parent).getCondition();
        if (switchValue == null) return null;
        final PsiType caseValueType = caseValue.getType();
        final PsiType switchValueType = switchValue.getType();
        return Pair.createNonNull(
          caseValueType == null ? PsiType.getJavaLangObject(label.getManager(), label.getResolveScope()) : caseValueType,
          switchValueType == null ? PsiType.getJavaLangObject(label.getManager(), label.getResolveScope()) : switchValueType
        );
      }
    };

  public GrCaseLabelImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitCaseLabel(this);
  }

  @Override
  public String toString() {
    return "Case label";
  }

  @Override
  @NotNull
  public PsiElement getKeywordToken() {
    final PsiElement keyword = getFirstChild();
    assert keyword != null && keyword.getNode() != null;
    final IElementType type = keyword.getNode().getElementType();
    assert type == GroovyTokenTypes.kCASE || type == GroovyTokenTypes.kDEFAULT;
    return keyword;
  }

  @Override
  public GrExpression getValue() {
    return findExpressionChild(this);
  }

  @Override
  public boolean isDefault() {
    final PsiElement firstChild = getFirstChild();
    assert firstChild != null;
    final ASTNode node = firstChild.getNode();
    assert node != null;
    return node.getElementType() == GroovyTokenTypes.kDEFAULT;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    if (isDefault()) return GroovyResolveResult.EMPTY_ARRAY;
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, RESOLVER);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement token = getKeywordToken();
    final PsiElement value = getValue();
    final int offset = token.getStartOffsetInParent();
    return new TextRange(
      offset, value == null
              ? offset + token.getTextLength()
              : value.getStartOffsetInParent() + value.getTextLength()
    );
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException("Case label cannot be renamed");
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Case label cannot be bound to anything");
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }
}
