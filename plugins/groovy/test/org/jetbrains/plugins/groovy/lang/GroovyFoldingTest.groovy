/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.folding.impl.CodeFoldingManagerImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Max Medvedev
 */
class GroovyFoldingTest extends LightCodeInsightFixtureTestCase {
  private String configure(String text) {
    myFixture.configureByText('____________a_______________.groovy', text)
    CodeFoldingManagerImpl.getInstance(getProject()).buildInitialFoldings(myFixture.editor);
    myFixture.doHighlighting()
    return text
  }

  private boolean assertFolding(int offset) {
    assert offset >= 0
    myFixture.editor.foldingModel.allFoldRegions.any {it.startOffset == offset}
  }

  private void assertFolding(String marker) {
    assert assertFolding(myFixture.file.text.indexOf(marker)), marker
  }

  private boolean assertNoFolding(int offset) {
    assert offset >= 0
    myFixture.editor.foldingModel.allFoldRegions.every {offset < it.startOffset || it.endOffset <= offset}
  }

  private void assertNoFolding(String marker) {
    assert assertNoFolding(myFixture.file.text.indexOf(marker)), marker
  }

  public void testEditingImports() {
    configure """
import java.util.List
import java.util.Map
<caret>

println 'hello'

class Foo { List a; Map b; }
"""

    assert myFixture.editor.foldingModel.getCollapsedRegionAtOffset(10)

    myFixture.type 'import '
    myFixture.doHighlighting()
    assert !myFixture.editor.foldingModel.getCollapsedRegionAtOffset(46)
  }


  void testOpenBlock() {
    configure '''def foo() {print 'a'}
def bar() {
  print 'a'
}'''
    assertNoFolding '{p'
    assertFolding '{\n'
  }

  void testClosureBlock() {
    configure '''def foo = {print 'a'}
def bar = {
  print 'a'
}'''
    assertNoFolding '{p'
    assertFolding '{\n'
  }

  void testClassBody() {
    configure '''\
class Single {//1
  def anonymous = new Runnable() {//2
    void run(){}
  }
  class Inner {//3
  }
}'''
    assertNoFolding '{//1'
    assertFolding('{//2')
    assertFolding('{//3')
  }

  void testComments() {
    configure '''
#!sh comment
/*single lime*/
/*multi
line*/

//one
//two

delimiter()
//single

/**single line doccomment */
def foo(){}

/**multiline
*/
def bar(){}
'''
    assertNoFolding('#!')
    assertNoFolding('/*single')
    assertFolding('/*multi')
    assertFolding('//one')
    assertNoFolding('//single')
    assertNoFolding('/**single')
    assertFolding('/**multi')
  }

  void testStrings() {
    configure '''\
def s1 = \'''1
\'''
def s2 = \'''2\'''
def s3 = """3
"""
def s4 = """4"""
def s5 = """5 ${text} defabc ${fg} """
def s6 = """6 ${text} def
abc ${fg}"""

def s7 = /7singlelinestring/
def s8 = /8print ${text} defabc ${fg} /
def s9 = /9print ${text}+- def
abc ${fg}/

def s10 = $/10singlelinestring/$
def s11 = $/11print ${text} defabc ${fg} /$
def s12 = $/12print ${text}-+ def
abc ${fg}/$
'''

    assertFolding('\'\'\'1')
    assertNoFolding('\'\'\'2')
    assertFolding('"""3')
    assertFolding('"""3')
    assertNoFolding('"""5')
    assertFolding('"""6')
    assertNoFolding('/7')
    assertNoFolding('/8')
    assertFolding('/9')
    assertFolding('+-')
    assertNoFolding('$/10')
    assertNoFolding('$/11')
    assertFolding('-+')
  }
}
