// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.HighlightingTest;

/**
 * @author Bas Leijdekkers
 */
public class GroovyHighlighting60Test extends LightGroovyTestCase implements HighlightingTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_6_0;
  }

  public void testSimpleVal() {
    highlightingTest("""
                       val x = 1
                       val (a, b) = [-1, 0]
                       for (val y : [1, 2, 3]) {
                         println y
                       }
                       class X {
                         val
                           x = 2
                       }
                       """);
  }

  public void testIncorrectVal() {
    highlightingTest(
      """
        class X {
          <error descr="Modifier 'val' not allowed on methods">val</error> x(val <error descr="Duplicate modifier 'val'">val</error> p) {
            val <error descr="Duplicate modifier 'val'">val</error> y = 1
          }
        }""");
  }

  public void testFinalEnum() {
    highlightingTest("<error descr=\"Modifier 'final' not allowed here\"><caret>final</error> enum E {}");
    myFixture.launchAction(myFixture.findSingleIntention("Remove 'final'"));
    myFixture.checkResult("enum E {}");
  }

  public void testWeakKeywordTypeDefinitions() {
    highlightingTest("""
                       class as {
                           as() {
                               new trait(1)
                           }
                       }
                       class record {
                           record() {}
                       }
                       class sealed {
                           sealed() {}
                       }
                       enum En {
                           as, permits, trait, sealed, record, var, val, yield
                       }
                       
                       class trait {
                           trait(var i) {}
                       }
                       class <error descr="'val' is a restricted type name and cannot be used as the identifier of a type declaration">val</error> {
                           val() {}
                       }
                       class <error descr="'var' is a restricted type name and cannot be used as the identifier of a type declaration">var</error> {
                           var() {}
                       }
                       class yield {
                           yield() {}
                       }
                       """);
  }

  public void testWeakKeywordImportAlias() {
    highlightingTest("""
                       import java.lang.String as trait
                       println (trait)"yes"
                       """);
  }

  public void testWeakKeywordLabeledStatement() {
    highlightingTest("""
                       trait: for (val x in [1, 2, 4]) {
                         if (x == 2) break trait;
                       }
                       """);
  }
}
