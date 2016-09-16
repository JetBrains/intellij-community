/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve


import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Medvedev Max
 */
class StaticCheckTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "resolve/static/"
  }

  protected void doTest(boolean staticOk) {
    final GroovyResolveResult resolveResult = advancedResolve("a.groovy")
    assertNotNull(resolveResult)
    final PsiElement element = resolveResult.element
    assertNotNull(element)
    assertEquals(staticOk, resolveResult.staticsOK)
  }

  void testPropInStaticInnerClass() {
    doTest(false)
  }

  void testThisInStaticInnerClass() {
    doTest(true)
  }

  void testWithInsideTheClass() {
    doTest(true)
  }
}
