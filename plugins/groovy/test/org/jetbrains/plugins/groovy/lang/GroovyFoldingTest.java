// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author Max Medvedev
 */
public class GroovyFoldingTest extends LightJavaCodeInsightFixtureTestCase {
  private void configure(String text) {
    myFixture.configureByText("____________a_______________.groovy", text);
    EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());
    myFixture.doHighlighting();
  }

  private boolean assertFolding(final int offset) {
    assertTrue("offset should be positive. current: " + offset, offset >= 0);
    FoldRegion[] regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : regions) {
      if (region.getStartOffset() == offset) return true;
    }
    return false;
  }

  private void assertFolding(String marker) {
    assert assertFolding(myFixture.getFile().getText().indexOf(marker)) : marker;
  }

  private boolean assertNoFolding(final int offset) {
    assertTrue("offset should be positive. current: " + offset, offset >= 0);
    FoldRegion[] regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : regions) {
      if (offset >= region.getStartOffset() && region.getEndOffset() > offset) return false;
    }
    return true;
  }

  private void assertNoFolding(String marker) {
    assert assertNoFolding(myFixture.getFile().getText().indexOf(marker)) : marker;
  }

  public void testEditingImports() {
    configure("""
                
                import java.util.List
                import java.util.Map
                <caret>
                
                println 'hello'
                
                class Foo { List a; Map b; }
                """);

    assertNotNull(myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(10));

    myFixture.type("import ");
    myFixture.doHighlighting();
    assertNull(myFixture.getEditor().getFoldingModel().getCollapsedRegionAtOffset(46));
  }

  public void testOpenBlock() {
    configure("""
                def foo() {print 'a'}
                def bar() {
                  print 'a'
                }""");
    assertNoFolding("{p");
    assertFolding("{\n");
  }

  public void testClosureBlock() {
    configure("""
                def foo = {print 'a'}
                def bar = {
                  print 'a'
                }""");
    assertNoFolding("{p");
    assertFolding("{\n");
  }

  public void testClassBody() {
    configure("""
                class Single {//1
                  def anonymous = new Runnable() {//2
                    void run(){}
                  }
                  class Inner {//3
                  }
                }""");
    assertNoFolding("{//1");
    assertFolding("{//2");
    assertFolding("{//3");
  }

  public void testComments() {
    configure("""
                
                #!sh comment
                /*single lime*/
                /*multi
                line*/
                
                //one
                //two
                //region test
                //three
                //four
                //endregion
                //five
                //six
                
                delimiter()
                //single
                
                /**single line doccomment */
                def foo(){}
                
                /**multiline
                */
                def bar(){}
                """);
    assertNoFolding("#!");
    assertNoFolding("/*single");
    assertFolding("/*multi");
    assertFolding("//one");
    assertFolding("//region test");
    assertFolding("//three");
    assertFolding("//five");
    assertNoFolding("//single");
    assertNoFolding("/**single");
    assertFolding("/**multi");
  }

  public void testStrings() {
    configure("""
                def s1 = '''1
                '''
                def s2 = '''2'''
                def s3 = ""\"3
                ""\"
                def s4 = ""\"4""\"
                def s5 = ""\"5 ${text} defabc ${fg} ""\"
                def s6 = ""\"6 ${text} def
                abc ${fg}""\"
                
                def s7 = /7singlelinestring/
                def s8 = /8print ${text} defabc ${fg} /
                def s9 = /9print ${text}+- def
                abc ${fg}/
                
                def s10 = $/10singlelinestring/$
                def s11 = $/11print ${text} defabc ${fg} /$
                def s12 = $/12print ${text}-+ def
                abc ${fg}/$
                """);

    assertFolding("'''1");
    assertNoFolding("'''2");
    assertFolding("\"\"\"3");
    assertFolding("\"\"\"3");
    assertNoFolding("\"\"\"5");
    assertFolding("\"\"\"6");
    assertNoFolding("/7");
    assertNoFolding("/8");
    assertFolding("/9");
    assertFolding("+-");
    assertNoFolding("$/10");
    assertNoFolding("$/11");
    assertFolding("-+");
  }
}
