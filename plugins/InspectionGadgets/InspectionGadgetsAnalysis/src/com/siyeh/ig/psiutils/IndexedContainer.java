/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an indexed container (java.util.List or array)
 *
 * @author Tagir Valeev
 */
public abstract class IndexedContainer {
  private final PsiExpression myQualifier;

  protected IndexedContainer(PsiExpression qualifier) {
    myQualifier = PsiUtil.skipParenthesizedExprDown(qualifier);
  }

  /**
   * Returns true if the supplied method reference maps index to the collection element
   *
   * @param methodReference method reference to check
   * @return true if the supplied method reference is element retrieval method reference
   */
  public abstract boolean isGetMethodReference(PsiMethodReferenceExpression methodReference);

  /**
   * Returns an ancestor element retrieval expression if the supplied expression is the index used in it
   * (e.g. index in arr[index] or in list.get(index))
   *
   * @param indexExpression index expression
   * @return a surrounding element retrieval expression or null if no element retrieval expression found
   */
  public abstract PsiExpression extractGetExpressionFromIndex(@Nullable PsiExpression indexExpression);

  /**
   * Extracts the element index if the supplied expression obtains the container element by index (either array[idx] or list.get(idx))
   *
   * @param expression expression to extract the index from
   * @return the extracted index or null if the supplied expression is not an element retrieval expression
   */
  public abstract PsiExpression extractIndexFromGetExpression(@Nullable PsiExpression expression);

  /**
   * @return the qualifier of the expression which was used to create this {@code IndexedContainer}. The extracted qualifier might be
   * non-physical if it was implicit in the original code (e.g. "this" could be returned if original call was simply "size()")
   */
  public PsiExpression getQualifier() {
    return myQualifier;
  }

  public boolean isQualifierEquivalent(@Nullable PsiExpression candidate) {
    candidate = PsiUtil.skipParenthesizedExprDown(candidate);
    return candidate != null && PsiEquivalenceUtil.areElementsEquivalent(myQualifier, candidate);
  }

  /**
   * @return type of the elements in the container or null if cannot be determined
   */
  public abstract PsiType getElementType();

  /**
   * Creates an IndexedContainer from length retrieval expression (like array.length or list.size())
   *
   * @param expression expression to create an IndexedContainer from
   * @return newly created IndexedContainer or null if the supplied expression is not length retrieval expression
   */
  @Nullable
  public static IndexedContainer fromLengthExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    PsiExpression arrayExpression = ExpressionUtils.getArrayFromLengthExpression(expression);
    if (arrayExpression != null) {
      return new ArrayIndexedContainer(arrayExpression);
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (ListIndexedContainer.isSizeCall(call)) {
        return new ListIndexedContainer(ExpressionUtils.getQualifierOrThis(call.getMethodExpression()));
      }
    }
    return null;
  }

  static class ArrayIndexedContainer extends IndexedContainer {
    ArrayIndexedContainer(PsiExpression qualifier) {
      super(qualifier);
    }

    @Override
    public boolean isGetMethodReference(PsiMethodReferenceExpression methodReference) {
      return false;
    }

    @Override
    public PsiExpression extractGetExpressionFromIndex(@Nullable PsiExpression indexExpression) {
      if (indexExpression != null) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(indexExpression.getParent());
        if (parent instanceof PsiExpression &&
            PsiTreeUtil.isAncestor(extractIndexFromGetExpression((PsiExpression)parent), indexExpression, false)) {
          return (PsiExpression)parent;
        }
      }
      return null;
    }

    @Override
    public PsiExpression extractIndexFromGetExpression(@Nullable PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression instanceof PsiArrayAccessExpression) {
        PsiArrayAccessExpression arrayAccess = (PsiArrayAccessExpression)expression;
        if (isQualifierEquivalent(arrayAccess.getArrayExpression())) {
          return arrayAccess.getIndexExpression();
        }
      }
      return null;
    }

    @Override
    public PsiType getElementType() {
      PsiType type = getQualifier().getType();
      return type instanceof PsiArrayType ? ((PsiArrayType)type).getComponentType() : null;
    }
  }

  static class ListIndexedContainer extends IndexedContainer {
    ListIndexedContainer(PsiExpression qualifier) {
      super(qualifier);
    }

    @Override
    public boolean isGetMethodReference(PsiMethodReferenceExpression methodReference) {
      if (!"get".equals(methodReference.getReferenceName())) return false;
      if (!isQualifierEquivalent(ExpressionUtils.getQualifierOrThis(methodReference))) return false;
      PsiMethod method = ObjectUtils.tryCast(methodReference.resolve(), PsiMethod.class);
      return method != null && MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_LIST, null, "get", PsiType.INT);
    }

    @Override
    public PsiExpression extractGetExpressionFromIndex(@Nullable PsiExpression indexExpression) {
      if (indexExpression != null) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(indexExpression.getParent());
        if (parent instanceof PsiExpressionList) {
          PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
          if (gParent instanceof PsiMethodCallExpression &&
              PsiTreeUtil.isAncestor(extractIndexFromGetExpression((PsiExpression)gParent), indexExpression, false)) {
            return (PsiExpression)gParent;
          }
        }
      }
      return null;
    }

    @Override
    public PsiExpression extractIndexFromGetExpression(@Nullable PsiExpression expression) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
      if (call == null) return null;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length == 1 && isGetCall(call) && isQualifierEquivalent(ExpressionUtils.getQualifierOrThis(call.getMethodExpression()))) {
        return args[0];
      }
      return null;
    }

    @Override
    public PsiType getElementType() {
      PsiType type = PsiUtil.substituteTypeParameter(getQualifier().getType(), CommonClassNames.JAVA_UTIL_LIST, 0, false);
      return GenericsUtil.getVariableTypeByExpressionType(type);
    }

    static boolean isGetCall(PsiMethodCallExpression call) {
      return MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_UTIL_LIST, null, "get", PsiType.INT);
    }

    static boolean isSizeCall(PsiMethodCallExpression call) {
      return MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_UTIL_LIST, PsiType.INT, "size");
    }
  }
}
