// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import groovy.transform.CompileStatic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import java.util.Collection;

import static com.intellij.testFramework.UsefulTestCase.*;

@CompileStatic
public interface ResolveTest extends BaseTest {

  @NotNull
  default <T extends PsiReference> T referenceByText(@NotNull String text, @NotNull Class<T> refType) {
    PsiReference ref = configureByText(text).findReferenceAt(getFixture().getCaretOffset());
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

  default <T extends PsiElement> void resolveTest(@NotNull String text, @Nullable Class<T> clazz) {
    Collection<? extends GroovyResolveResult> results = multiResolveByText(text);
    if (clazz == null) {
      assertEmpty(results);
    }
    else {
      PsiElement resolved = assertOneElement(results).getElement();
      assertInstanceOf(resolved, clazz);
    }
  }
}
