// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo;

import java.util.Collection;

import static com.intellij.testFramework.UsefulTestCase.*;
import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType;
import static org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt.getDelegatesToInfo;

public interface ResolveTest extends BaseTest {

  @NotNull
  default <T extends PsiReference> T referenceByText(@NotNull String text, @NotNull Class<T> refType) {
    configureByText(text);
    return referenceUnderCaret(refType);
  }

  @NotNull
  default <T extends PsiReference> T referenceUnderCaret(@NotNull Class<T> refType) {
    PsiReference ref = getPsiFile().findReferenceAt(getFixture().getCaretOffset());
    return assertInstanceOf(ref, refType);
  }

  @NotNull
  default GroovyReference referenceByText(@NotNull String text) {
    return referenceByText(text, GroovyReference.class);
  }

  @NotNull
  default GroovyResolveResult advancedResolveByText(@NotNull String text) {
    return referenceByText(text).advancedResolve();
  }

  @NotNull
  default Collection<? extends GroovyResolveResult> multiResolveByText(@NotNull String text) {
    return referenceByText(text).resolve(false);
  }

  default <T extends PsiElement> T resolveTest(@NotNull String text, @Nullable Class<T> clazz) {
    return resolveTest(referenceByText(text), clazz);
  }

  default <T extends PsiElement> T resolveTest(@Nullable Class<T> clazz) {
    return resolveTest(referenceUnderCaret(GroovyReference.class), clazz);
  }

  default <T extends PsiElement> T resolveTest(@NotNull GroovyReference reference, @Nullable Class<T> clazz) {
    Collection<? extends GroovyResolveResult> results = reference.resolve(false);
    if (clazz == null) {
      assertEmpty(results);
      return null;
    }
    else {
      PsiElement resolved = assertOneElement(results).getElement();
      return assertInstanceOf(resolved, clazz);
    }
  }

  default void closureDelegateTest(@NotNull String fqn, int strategy) {
    GrClosableBlock closure = elementUnderCaret(GrClosableBlock.class);
    DelegatesToInfo delegatesToInfo = getDelegatesToInfo(closure);
    assertNotNull(delegatesToInfo);
    assertType(fqn, delegatesToInfo.getTypeToDelegate());
    assertEquals(strategy, delegatesToInfo.getStrategy());
  }

  default void methodTest(@NotNull PsiMethod method, String name, String fqn) {
    assertEquals(name, method.getName());
    PsiClass containingClass = method.getContainingClass();
    if (fqn == null) {
      assertNull(containingClass);
    }
    else {
      assertNotNull(containingClass);
      assertEquals(fqn, containingClass.getQualifiedName());
    }
  }
}
