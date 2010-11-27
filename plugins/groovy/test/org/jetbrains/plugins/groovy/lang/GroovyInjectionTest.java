/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;

import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class GroovyInjectionTest extends LightCodeInsightFixtureTestCase {
  public void testIntelliLangInjections() throws Exception {
    myFixture.addClass("package groovy.lang; public class GroovyShell { void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", "void f() { new groovy.lang.GroovyShell().evaluate(\"s = new String()\") }");
    assertNotNull(psiFile);
    final PsiElement elementAt = psiFile.findElementAt(psiFile.getText().indexOf('\"') + 1);
    assertNotNull(elementAt);
    InjectedLanguageUtil.forceInjectionOnElement(elementAt);
    final List<Pair<PsiElement,TextRange>> files = InjectedLanguageUtil.getInjectedPsiFiles(elementAt);
  }
}
