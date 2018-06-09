// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf

@CompileStatic
trait ResolveTest {

  abstract CodeInsightTestFixture getFixture()

  GroovyFile configureByText(String text) {
    return (GroovyFile)fixture.configureByText('_.groovy', text)
  }

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
}
