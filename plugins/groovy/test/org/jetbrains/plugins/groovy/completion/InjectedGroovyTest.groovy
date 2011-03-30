/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.completion

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry
import org.jetbrains.plugins.groovy.GroovyFileType

/**
 * @author peter
 */
class InjectedGroovyTest extends LightCodeInsightFixtureTestCase {

  public void testTabMethodParentheses() {
    myFixture.configureByText("a.xml", """<groovy>
String s = "foo"
s.codePo<caret>charAt(0)
</groovy>""")

    def host = PsiTreeUtil.findElementOfClassAtOffset(myFixture.file, myFixture.editor.caretModel.offset, XmlText, false)
    TemporaryPlacesRegistry.getInstance(project).getLanguageInjectionSupport().addInjectionInPlace(GroovyFileType.GROOVY_LANGUAGE, host);

    myFixture.completeBasic()
    myFixture.type('\t')
    myFixture.checkResult("""<groovy>
String s = "foo"
s.codePointAt(<caret>0)
</groovy>""")
  }

}
