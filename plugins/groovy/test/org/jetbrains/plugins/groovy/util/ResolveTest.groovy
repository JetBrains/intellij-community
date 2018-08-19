// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf

@CompileStatic
trait ResolveTest extends BaseTest {

  def <T extends PsiReference> T referenceByText(String text, Class<T> refType) {
    final ref = configureByText(text).findReferenceAt(fixture.caretOffset)
    assertInstanceOf ref, refType
    return (T)ref
  }

  GroovyReference referenceByText(String text) {
    return referenceByText(text, GroovyReference)
  }

  Collection<? extends GroovyResolveResult> multiResolveByText(String text) {
    referenceByText(text).resolve(false)
  }

  def <T extends PsiElement> void resolveTest(String text, Class<T> clazz) {
    def results = multiResolveByText(text)
    if (clazz == null) {
      assert results.isEmpty()
    }
    else {
      assert results.size() == 1
      def resolved = results[0].element
      assertInstanceOf(resolved, clazz)
    }
  }
}
