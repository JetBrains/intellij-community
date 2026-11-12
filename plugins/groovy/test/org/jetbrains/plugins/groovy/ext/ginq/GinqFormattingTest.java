// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;

@SuppressWarnings("SpellCheckingInspection")
@TestApplication
public class GinqFormattingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GinqTestUtils.getProjectDescriptor());
  private final TestFixture<GroovyFormatterFixture> formatter = groovyFormatterFixture(projectFixture);

  public void doEnterTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    myFixture.configureByText("a.groovy", before);
    myFixture.type('\n');
    myFixture.checkResult(after, true);
  }

  @Test
  public void testBasicFragmentFormatting() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testBasicFragmentFormatting2() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testBasicFragmentFormatting3() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testOn() {
    formatter.get().getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.get().checkFormatting("""
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

  @Test
  public void testOn2() {
    formatter.get().getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.get().checkFormatting("""
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

  @Test
  public void testOn3() {
    formatter.get().getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.get().checkFormatting("""
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

  @Test
  public void testOn4() {
    formatter.get().getGroovyCustomSettings().GINQ_ON_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.get().checkFormatting("""
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

  @Test
  public void testHaving() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testFormattingForUntransformed() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testUntransformedInFrom() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testUntransformedInJoin() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testUntransformedInOn() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testSpaceAfterCall() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testNoSpaceAfterCall() {
    formatter.get().getGroovyCustomSettings().GINQ_SPACE_AFTER_KEYWORD = false;
    formatter.get().checkFormatting("""
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

  @Test
  public void testWrapGinqFragments() {
    formatter.get().getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.get().checkFormatting("""
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

  @Test
  public void testWrapGinqFragments2() {
    formatter.get().getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.get().checkFormatting("""
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

  @Test
  public void testWrapGinqFragments3() {
    formatter.get().getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.get().checkFormatting("""
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

  @Test
  public void testWrapGinqFragments4() {
    formatter.get().getGroovyCustomSettings().GINQ_GENERAL_CLAUSE_WRAP_POLICY = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.get().checkFormatting("""
                      GQ {
                        from x in [1] select x
                      }
                      """, """
                      GQ {
                        from x in [1] select x
                      }
                      """);
  }

  @Test
  public void testBlankLines() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testFormatNestedGinq() {
    formatter.get().checkFormatting("""
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testIndentOn() {
    formatter.get().getGroovyCustomSettings().GINQ_INDENT_ON_CLAUSE = false;
    formatter.get().checkFormatting("""
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

  @Test
  public void testIndentHaving() {
    formatter.get().getGroovyCustomSettings().GINQ_INDENT_HAVING_CLAUSE = false;
    formatter.get().checkFormatting("""
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

  @Test
  public void testIncorrectGinq() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testNested() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testNested2() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testMethodGinq1() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testMethodFormatNestedGinq() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testMethodIncorrectGinq() {
    formatter.get().checkFormatting("""
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
}
