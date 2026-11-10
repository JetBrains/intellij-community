// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GantReferenceCompletionTest {
  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "gant/completion");
  }

  private static final LightProjectDescriptor GANT_PROJECT =
    new LibraryLightProjectDescriptor(GroovyProjectDescriptors.LIB_GROOVY_1_7.plus(
      new RepositoryTestLibrary("org.codehaus.gant:gant_groovy1.7:1.9.7")));
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GANT_PROJECT);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  
  public void complete(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.gant", text);
    myFixture.completeBasic();
  }

  public void checkVariants(String text, String... items) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    complete(text);
    assertTrue(myFixture.getLookupElementStrings().containsAll(DefaultGroovyMethods.asType(items, List.class)));
  }

  @Test
  public void testDep() {
    checkVariants("""
                    target(aaa: "") {
                        dep<caret>
                    }
                    """, "depends", "dependset");
  }

  @Test
  public void testAntBuilderJavac() {
    checkVariants("""
                    target(aaa: "") {
                        ant.jav<caret>
                    }""", "java", "javac", "javadoc", "javadoc2", "javaresource");
  }

  @Test
  public void testAntJavacTarget() {
    checkVariants("""
                    target(aaa: "") {
                        jav<caret>
                    }""", "java", "javac", "javadoc", "javadoc2", "javaresource");
  }

  @Test
  public void testInclude() {
    checkVariants("inc<caret>", "include", "includeTool", "includeTargets");
  }

  @Test
  public void testMutual() {
    checkVariants("""
                    target(genga: "") { }
                    target(aaa: "") {
                        depends(geng<caret>x)
                    }""", "genga");
  }

  @Test
  public void testUnknownQualifier() {
    complete("""
               target(aaa: "") {
                   foo.jav<caret>
               }""");
  }

  @Test
  public void testTopLevelNoAnt() {
    complete("jav<caret>");
  }

  @Test
  public void testInMethodNoAnt() {
    complete("""
               target(aaa: "") {
                 foo()
               }
               
               def foo() {
                 jav<caret>
               }
               """);
  }

  @Test
  public void testPatternset() {
    checkVariants("ant.patt<caret>t", "patternset");
  }

  @Test
  public void testTagsInsideTags() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      AntBuilder ant
      ant.zip {
        patternset {
          includ<caret>
        }
      }""");
    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsOnly("include", "includesfile");
  }

  @Test
  public void testTagsInsideTagsInGantTarget() {
    checkVariants("""
                    target(aaa: "") {
                      zip {
                        patternset {
                          includ<caret>
                        }
                      }
                    }""", "include", "includesfile", "includeTargets", "includeTool");
  }

  @Test
  public void testUntypedTargets() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GroovyUntypedAccessInspection());
    myFixture.configureByText("a.gant", """
      target (default : '') {
              echo(message: 'Echo task.')
              copy(file: 'from.txt', tofile: 'to.txt')
              delete(file: 'to.txt')
      }""");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testStringTargets() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
    myFixture.configureByText("a.gant", """
      target (default : '') {
        echo("hello2")
        echo(message: 'Echo task.')
        ant.fail('Failure reason')
        ant.junit(fork:'yes') {}
      }""");
    myFixture.checkHighlighting(true, false, false);
  }
}
