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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.LightGroovyTestCase

/**
 * @author Max Medvedev
 */
class GrReturnPointHighlightingTest extends LightGroovyTestCase {
  final String basePath = null

  void testReturnPoint1() {
    doTest('''\
private static getWorldType(stepFile) {
    final worldType //unused
    if (som) throw new IOException()

    for (statement in stepFile.statements) {
        if (statement instanceof String && isWorldDeclaration(statement)) {
            final closure = getClosureArg(statement)
            re<caret>turn closure.returnType
        }
    }
}
''', 'throw new IOException()', 'return closure.returnType')
  }

  void testReturnPoint2() {
    doTest('''\
private static getWorldType(stepFile) {
    final worldType //unused
    if (som) throw new IOException()

    for (statement in stepFile.statements) {
        if (statement instanceof String && isWorldDeclaration(statement)) {
            final closure = getClosureArg(statement)
            re<caret>turn closure.returnType
        }
    }
    2
}
''', 'throw new IOException()', 'return closure.returnType', '2')
  }

  private void doTest(final String text, final String... usages) {
    myFixture.configureByText('_.groovy', text)
    HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(myFixture.editor, myFixture.file);
    assertNotNull(handler);
    List<PsiElement> targets = handler.targets;
    assertEquals(1, targets.size());
    assertEquals("return", targets.get(0).getText());

    handler.computeUsages(targets);
    List<TextRange> readUsages = handler.readUsages;
    assertEquals(usages.length, readUsages.size());

    final List<String> textUsages = readUsages.collect { fileTextOfRange(it) }
    assertSameElements(Arrays.asList(usages), textUsages)
  }

  protected String fileTextOfRange(TextRange textRange) {
    return myFixture.file.text.substring(textRange.startOffset, textRange.endOffset);
  }
}
