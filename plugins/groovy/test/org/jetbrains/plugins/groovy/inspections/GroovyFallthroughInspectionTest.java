// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyFallthroughInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class GroovyFallthroughInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  private void doTest(final String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", text);

    myFixture.enableInspections(GroovyFallthroughInspection.class);
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testBasic() {
    doTest("""
             def x = 1.23
             def result = ""
             
             switch ( x ) {
                 case "foo":
                     result = "found foo"
                     // lets fall through
             
                 case "bar":
                     result += "bar"
             
                 <warning descr="Fallthrough in 'switch' statement">case</warning> [4, 5, 6, 'inList']:
                     result = "list"
                     break
             
                 case 12..30:
                     result = "range"
                     break
             
                 case Integer:
                     result = "integer"
                     break
             
                 case Number:
                     result = "number"
                     break
             
                 default:
                     result = "default"
             }
             
             assert result == "number"
             """);
  }

  @Test
  public void testNoInspectionWithArrow() {
    doTest("""
             def x = 1
             switch(x) {
               case 10 -> 20
               case 20 -> 30
               default -> 40
             }
             """);
  }

  @Test
  public void testCheckYield() {
    doTest("""
             def x = 1
             def y = switch(x) {
               case 1:
               <warning>case</warning> 10:
                 yield 20
               case 20:\s
                 yield 30
               default:\s
                 yield 40
             }
             """);
  }
}
