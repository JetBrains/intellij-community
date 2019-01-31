// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction
import com.intellij.openapi.actionSystem.AnAction
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class GrCommentTest extends GroovyFormatterTestCase {
  @Override
  protected String getBasePath() {
    TestUtils.testDataPath + 'grComment/'
  }

  void testUncommentLine() {
    lineTest('''\
//<caret>print 2
''', '''\
print 2
<caret>''')
  }


  void testBlock0() {
    blockTest('''\
<selection>print 2</selection>
''', '''\
<selection>/*print 2*/</selection>
''')
  }

  void testUncommentBlock0() {
    blockTest('''\
<selection>/*print 2*/</selection>
''', '''\
<selection>print 2</selection>
''')
  }

  void testBlock1() {
    blockTest('''\
<selection>print 2
</selection>
''', '''\
<selection>/*
print 2
*/
</selection>
''')
  }

  void testUncommentBlock1() {
    blockTest('''\
<selection>/*
print 2
*/
</selection>
''', '''\
<selection>print 2
</selection>
''')
  }

  void 'test line comment no indent'() {
    lineTest '''\
def foo() {
  <caret>print 2
}
''', '''\
def foo() {
//  print 2
}<caret>
'''
  }

  void 'test line comment indent'() {
    groovySettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    lineTest '''\
def foo() {
  println 42<caret>
}
''', '''\
def foo() {
  //println 42
}<caret>
'''
  }

  void 'test line comment indent and space'() {
    groovySettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    groovySettings.LINE_COMMENT_ADD_SPACE = true
    lineTest '''\
def foo() {
  println 42<caret>
}
''', '''\
def foo() {
  // println 42
}<caret>
'''
  }

  void lineTest(String before, String after) {
    doTest(before, after, new CommentByLineCommentAction())
  }

  void blockTest(String before, String after) {
    doTest(before, after, new CommentByBlockCommentAction())
  }

  private void doTest(@NotNull String before, @NotNull String after, final AnAction action) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before)
    myFixture.testAction(action)
    myFixture.checkResult(after)
  }
}
