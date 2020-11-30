// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.analysis.AnalysisBundle
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.HighlightingTest
import org.junit.Test

@SuppressWarnings('SpellCheckingInspection')
@CompileStatic
class GrSuppressionTest extends GroovyLatestTest implements HighlightingTest {

  private void doTest(String before, String after) {
    fixture.enableInspections SpellCheckingInspection
    configureByText before
    fixture.checkHighlighting()
    fixture.launchAction fixture.findSingleIntention(AnalysisBundle.message("suppress.inspection.file"))
    fixture.checkResult after
  }

  @Test
  void 'suppress by file level comment'() {
    doTest '''\



println("<TYPO><caret>abcdef</TYPO>")
''', '''\
//file:noinspection SpellCheckingInspection



println("abcdef")
'''
  }

  @Test
  void 'suppress by file level comment after another comment'() {
    doTest '''\
/* some other comment */



println("<TYPO><caret>abcdef</TYPO>")
''', '''\
/* some other comment */
//file:noinspection SpellCheckingInspection



println("abcdef")
'''
  }

  @Test
  void 'suppress by file level comment after hash bang'() {
    doTest '''\
#!/usr/bin/env groovy
// some other comment



println("<TYPO><caret>abcdef</TYPO>")
''', '''\
#!/usr/bin/env groovy
// some other comment
//file:noinspection SpellCheckingInspection



println("abcdef")
'''
  }
}
