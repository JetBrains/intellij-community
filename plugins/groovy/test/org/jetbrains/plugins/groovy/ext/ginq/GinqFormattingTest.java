// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase;

@SuppressWarnings("SpellCheckingInspection")
public class GinqFormattingTest extends GroovyFormatterTestCase {
  public void doEnterTest(String before, String after) {
    myFixture.configureByText("a.groovy", before);
    myFixture.type('\n');
    myFixture.checkResult(after, true);
  }

  public void testBasicFragmentFormatting() {
    checkFormatting("""
                      GQ {
                        from x in [1]
                          select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select x
                      }
                      """);
  }

  public void testBasicFragmentFormatting2() {
    checkFormatting("""
                      GQ {
                      from x in [1]
                      select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select x
                      }
                      """);
  }

  public void testBasicFragmentFormatting3() {
    checkFormatting("""
                      GQ {
                          from x in [1]
                          select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select x
                      }
                      """);
  }

  public void testOn() {
    getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.DO_NOT_WRAP;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        join y in [2]\s
                          on x == y
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        join y in [2] on x == y
                        select x
                      }
                      """);
  }

  public void testOn2() {
    getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_ALWAYS;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        join y in [2] on x == y
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        join y in [2]
                          on x == y
                        select x
                      }
                      """);
  }

  public void testOn3() {
    getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        join y in [2] on x == y
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        join y in [2] on x == y
                        select x
                      }
                      """);
  }

  public void testOn4() {
    getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        join y in [2]
                          on x == y
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        join y in [2]
                          on x == y
                        select x
                      }
                      """);
  }

  public void testHaving() {
    checkFormatting("""
                      GQ {
                        from x in [1]
                        groupby x
                            having x == y
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        groupby x
                          having x == y
                        select x
                      }
                      """);
  }

  public void testFormattingForUntransformed() {
    checkFormatting("""
                      GQ {
                        from x in [1]
                        where x == y
                                              && y == y
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        where x == y
                            && y == y
                        select x
                      }
                      """);
  }

  public void testUntransformedInFrom() {
    checkFormatting("""
                      GQ {
                        from x in [1     ]
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select x
                      }
                      """);
  }

  public void testUntransformedInJoin() {
    checkFormatting("""
                      GQ {
                        from x in [1]
                        join y in [2     ]
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        join y in [2]
                        select x
                      }
                      """);
  }

  public void testUntransformedInOn() {
    checkFormatting("""
                      GQ {
                        from x in [1]
                        join y in [2     ]
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        join y in [2]
                        select x
                      }
                      """);
  }

  public void testSpaceAfterCall() {
    checkFormatting("""
                      GQ {
                        from x in [1]
                        select(x)
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select (x)
                      }
                      """);
  }

  public void testNoSpaceAfterCall() {
    getGroovyCustomSettings().GINQ_SPACE_AFTER_KEYWORD = false;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        select(x)
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select(x)
                      }
                      """);
  }

  public void testWrapGinqFragments() {
    getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_ALWAYS;
    checkFormatting("""
                      GQ {
                        from x in [1] select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select x
                      }
                      """);
  }

  public void testWrapGinqFragments2() {
    getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.DO_NOT_WRAP;
    checkFormatting("""
                      GQ {
                        from x in [1]\s
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1] select x
                      }
                      """);
  }

  public void testWrapGinqFragments3() {
    getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        select x
                      }
                      """);
  }

  public void testWrapGinqFragments4() {
    getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    checkFormatting("""
                      GQ {
                        from x in [1] select x
                      }
                      """, """
                      GQ {
                        from x in [1] select x
                      }
                      """);
  }

  public void testBlankLines() {
    checkFormatting("""
                      GQ {
                        from x in [1]
                      
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                      
                        select x
                      }
                      """);
  }

  public void testFormatNestedGinq() {
    checkFormatting("""
                      GQ {
                        from x in (from y in [2] select y)
                        select x
                      }
                      """, """
                      GQ {
                        from x in (from y in [2]
                                   select y)
                        select x
                      }
                      """);
  }

  public void testEnter() {
    doEnterTest("""
                  GQ {
                    from x in [1]<caret>
                    select x
                  }
                  """, """
                  GQ {
                    from x in [1]
                    <caret>
                    select x
                  }
                  """);
  }

  public void testEnter2() {
    doEnterTest("""
                  GQ {
                    from x in (from y in [2]<caret>
                               select y)
                    select x
                  }
                  """, """
                  GQ {
                    from x in (from y in [2]
                               <caret>
                               select y)
                    select x
                  }
                  """);
  }

  public void testEnter3() {
    doEnterTest("""
                  GQ {<caret>
                    from x in [1]
                    select x
                  }
                  """, """
                  GQ {
                    <caret>
                    from x in [1]
                    select x
                  }
                  """);
  }

  public void testIndentOn() {
    getGroovyCustomSettings().GINQ_INDENT_ON_CLAUSE = false;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        join y in [2]
                        on x == y
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        join y in [2]
                        on x == y
                        select x
                      }
                      """);
  }

  public void testIndentHaving() {
    getGroovyCustomSettings().GINQ_INDENT_HAVING_CLAUSE = false;
    checkFormatting("""
                      GQ {
                        from x in [1]
                        groupby x
                        having x == x
                        select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        groupby x
                        having x == x
                        select x
                      }
                      """);
  }

  public void testIncorrectGinq() {
    checkFormatting("""
                      GQ {
                          from x in [1]
                          orderby x in
                          limit 1, 2
                          select x
                      }
                      """, """
                      GQ {
                        from x in [1]
                        orderby x in
                            limit 1, 2
                        select x
                      }
                      """);
  }

  public void testNested() {
    checkFormatting("""
                      def foo() {
                        GQ {
                          from x in [1]
                          select x
                        }
                      }
                      """, """
                      def foo() {
                        GQ {
                          from x in [1]
                          select x
                        }
                      }
                      """);
  }

  public void testNested2() {
    checkFormatting("""
                      def baz() {
                        def foo() {
                          GQ {
                            from x in [1]
                            select x
                          }
                        }
                      }
                      """, """
                      def baz() {
                        def foo() {
                          GQ {
                            from x in [1]
                            select x
                          }
                        }
                      }
                      """);
  }

  public void testMethodGinq1() {
    checkFormatting("""
                      import groovy.ginq.transform.GQ
                      
                      @GQ
                      def foo() {
                          from x in [1]
                          select x
                      }
                      """, """
                      import groovy.ginq.transform.GQ
                      
                      @GQ
                      def foo() {
                        from x in [1]
                        select x
                      }
                      """);
  }

  public void testMethodFormatNestedGinq() {
    checkFormatting("""
                      import groovy.ginq.transform.GQ
                      
                      @GQ
                      def foo() {
                        from x in (from y in [2] select y)
                        select x
                      }
                      """, """
                      import groovy.ginq.transform.GQ
                      
                      @GQ
                      def foo() {
                        from x in (from y in [2]
                                   select y)
                        select x
                      }
                      """);
  }

  public void testMethodIncorrectGinq() {
    checkFormatting("""
                      import groovy.ginq.transform.GQ
                      
                      @GQ
                      def foo() {
                          from x in [1]
                          orderby x in
                          limit 1, 2
                          select x
                      }
                      """, """
                      import groovy.ginq.transform.GQ
                      
                      @GQ
                      def foo() {
                        from x in [1]
                        orderby x in
                            limit 1, 2
                        select x
                      }
                      """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GinqTestUtils.getProjectDescriptor();
  }
}
