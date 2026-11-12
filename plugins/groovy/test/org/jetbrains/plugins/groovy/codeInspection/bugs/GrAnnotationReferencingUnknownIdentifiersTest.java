// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GrAnnotationReferencingUnknownIdentifiersTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  private static final String myTuplePrefix = """
    import groovy.transform.TupleConstructor
    """;
  private static final String myMapPrefix = """
      import groovy.transform.MapConstructor
      """;

  private void doTest(String before) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GrAnnotationReferencingUnknownIdentifiers());
    if (before.contains("TupleConstructor")) before = myTuplePrefix + before;
    if (before.contains("MapConstructor")) before = myMapPrefix + before;
    myFixture.configureByText("_.groovy", before);
    myFixture.checkHighlighting();
  }

  @Test
  public void test_includes() {
    doTest("""
             
             @TupleConstructor(includes = ["a", "b", <warning>"c"</warning>])
             class Rr {
               boolean a
               String b
             }
             """);
  }

  @Test
  public void test_excludes() {
    doTest("""
             
             @TupleConstructor(excludes = ["a", "b", <warning>"c"</warning>])
             class Rr {
               boolean a
               String b
             }
             """);
  }

  @Test
  public void test_raw_string() {
    doTest("""
             
             @TupleConstructor(includes = "  a, b   ,   <warning>c</warning>")
             class Rr {
               boolean a
               String b
             }
             """);
  }

  @Test
  public void test_map_constructor() {
    doTest("""
             
             @MapConstructor(includes = "  aa, bb  , <warning>cd</warning>")
             class Rr {
               boolean aa
               String bb
             }
             """);
  }

  @Test
  public void test_super_class() {
    doTest("""
             
             class Nn {
               String a
             }
             
             @TupleConstructor(includes = " a,  b,  <warning>c</warning>", includeSuperProperties = true)
             class Rr extends Nn {
               int b
             }
             """);
  }

  @Test
  public void test_ignored_super_class() {
    doTest("""
             
             class Nn {
               String a
             }
             
             @TupleConstructor(includes = " <warning>a</warning>,  b,  <warning>c</warning>")
             class Rr extends Nn {
               int b
             }
             """);
  }
}