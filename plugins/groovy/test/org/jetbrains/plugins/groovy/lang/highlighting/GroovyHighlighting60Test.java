// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class GroovyHighlighting60Test implements HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_6_0_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(codeInsightFixture.get());
  }

  @Test
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

  @Test
  public void testIncorrectVal() {
    highlightingTest(
      """
        class X {
          <error descr="Modifier 'val' not allowed on methods">val</error> x(val <error descr="Duplicate modifier 'val'">val</error> p) {
            val <error descr="Duplicate modifier 'val'">val</error> y = 1
          }
        }""");
  }

  @Test
  public void testFinalEnum() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    highlightingTest("<error descr=\"Modifier 'final' not allowed here\"><caret>final</error> enum E {}");
    myFixture.launchAction(myFixture.findSingleIntention("Remove 'final'"));
    myFixture.checkResult("enum E {}");
  }

  @Test
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

  @Test
  public void testModuleImport() {
    highlightingTest("""
                       import module java.sql
                       <warning descr="Unused import">import module java.base <error descr="Import alias not allowed on module import">as Basic</error></warning>
                       def conn = DriverManager.getConnection('jdbc:h2:mem:test')
                       def stmt = conn.createStatement()
                       """);
  }

  @Test
  public void testAnnotatedImport() {
    highlightingTest("""
                       @<error descr="'@Deprecated' annotation not allowed on import">Deprecated</error>
                       <warning descr="Unused import">import java.lang.String</warning>
                       """);
  }

  @Test
  public void testAnnotatedLoop() {
    highlightingTest("""
                       import groovy.transform.Parallel
                       var items = [1, 2, 3]
                       @Parallel
                       @<error descr="'@Deprecated' annotation not allowed on loop">Deprecated</error>
                       for (item in items) { println(item) }
                       """);
  }

  @Test
  public void testWeakKeywordImportAlias() {
    highlightingTest("""
                       import java.lang.String as trait
                       println (trait)"yes"
                       """);
  }

  @Test
  public void testWeakKeywordLabeledStatement() {
    highlightingTest("""
                       trait: for (val x in [1, 2, 4]) {
                         if (x == 2) break trait;
                       }
                       """);
  }
}
