/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

/**
 * @author Sergey Evdokimov
 */
class GroovyResolveFileWithContextTest extends LightCodeInsightFixtureTestCase {

  public void testResolve() {
    GroovyFile context = GroovyPsiElementFactory.getInstance(project)
      .createGroovyFile("class DummyClass {" +
                        " String sss1 = null;" +
                        "}" +
                        "", false, null);

    GroovyFile file = GroovyPsiElementFactory.getInstance(project)
      .createGroovyFile("""
String sss2;
def x = sss1 + sss2;
""", false, null);

    file.setContext(context.lastChild)

    checkResolved(file, "sss1")
    checkResolved(file, "sss2")
  }

  private static void checkResolved(PsiFile file, String referenceText) {
    int idx = file.text.lastIndexOf(referenceText)
    assert idx > 0;
    def e = file.findElementAt(idx)

    while (e != null && !(e instanceof GrReferenceExpression)) {
      e = e.parent
    }

    assert e != null
    assert e.getTextLength() == referenceText.length()
    assert e.resolve() != null : referenceText
  }

}
