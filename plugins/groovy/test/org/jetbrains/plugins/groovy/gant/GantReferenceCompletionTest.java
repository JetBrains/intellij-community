// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class GantReferenceCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor GANT_PROJECT =
    new LibraryLightProjectDescriptor(GroovyProjectDescriptors.LIB_GROOVY_1_7.plus(
      new RepositoryTestLibrary("org.codehaus.gant:gant_groovy1.7:1.9.7")));
  
  public void complete(String text) {
    myFixture.configureByText("a.gant", text);
    myFixture.completeBasic();
  }

  public void checkVariants(String text, String... items) {
    complete(text);
    assertTrue(myFixture.getLookupElementStrings().containsAll(DefaultGroovyMethods.asType(items, List.class)));
  }

  public void testDep() {
    checkVariants("""
                    target(aaa: "") {
                        dep<caret>
                    }
                    """, "depends", "dependset");
  }

  public void testAntBuilderJavac() {
    checkVariants("""
                    target(aaa: "") {
                        ant.jav<caret>
                    }""", "java", "javac", "javadoc", "javadoc2", "javaresource");
  }

  public void testAntJavacTarget() {
    checkVariants("""
                    target(aaa: "") {
                        jav<caret>
                    }""", "java", "javac", "javadoc", "javadoc2", "javaresource");
  }

  public void testInclude() {
    checkVariants("inc<caret>", "include", "includeTool", "includeTargets");
  }

  public void testMutual() {
    checkVariants("""
                    target(genga: "") { }
                    target(aaa: "") {
                        depends(geng<caret>x)
                    }""", "genga");
  }

  public void testUnknownQualifier() {
    complete("""
               target(aaa: "") {
                   foo.jav<caret>
               }""");
  }

  public void testTopLevelNoAnt() {
    complete("jav<caret>");
  }

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

  public void testPatternset() {
    checkVariants("ant.patt<caret>t", "patternset");
  }

  public void testTagsInsideTags() {
    myFixture.configureByText("a.groovy", """
      AntBuilder ant
      ant.zip {
        patternset {
          includ<caret>
        }
      }""");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "include", "includesfile");
  }

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

  public void testUntypedTargets() {
    myFixture.enableInspections(new GroovyUntypedAccessInspection());
    myFixture.configureByText("a.gant", """
      target (default : '') {
              echo(message: 'Echo task.')
              copy(file: 'from.txt', tofile: 'to.txt')
              delete(file: 'to.txt')
      }""");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testStringTargets() {
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

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GANT_PROJECT;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "gant/completion";
  }
}
