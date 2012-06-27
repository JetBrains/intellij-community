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

import com.intellij.psi.PsiFile

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry
import org.jetbrains.plugins.groovy.GroovyFileType

import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil

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

  public void testIntelliLangInjections() throws Exception {
    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", 'new groovy.lang.GroovyShell().evaluate("s = new String()")');
    assertNotNull(psiFile);

    def offset = psiFile.getText().indexOf('"') + 1
    assertNotNull(psiFile.findElementAt(offset));
    assert InjectedLanguageUtil.findInjectedPsiNoCommit(psiFile, offset)
  }

  public void testRegexInjections() {
    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", 'new groovy.lang.GroovyShell().evaluate(/ blah-blah-blah \\ language won\'t be injected here /)');
    assertNotNull(psiFile);

    assert InjectedLanguageUtil.findInjectedPsiNoCommit(psiFile, psiFile.getText().indexOf('blah') + 1)
    assert InjectedLanguageUtil.findInjectedPsiNoCommit(psiFile, psiFile.getText().indexOf('injected') + 1)
  }

}
