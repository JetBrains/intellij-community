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
public class GrNamedVariantLabelsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testBasic() {
    doTest("""
             
             @NamedVariant
             def foo(String s, @NamedParam Integer p) {}
             
             foo("", <warning>s</warning> : "", p : 1)
             """);
  }

  @Test
  public void testConstructor() {
    doTest("""
             
             class Rr {
                 @NamedVariant
                 Rr(String s, @NamedParam Integer p) {}
             }
             
             new Rr("", <warning>s</warning> : "", p : 1)
             """);
  }

  @Test
  public void testNoinspection() {
    doTest("""
             
             class Rr {
                 @NamedVariant
                 Rr(String s, Integer p) {}
             }
             
             new Rr(s : "", p : 1)
             """);
  }

  @Test
  public void testNoinspection2() {
    doTest("""
             
             class Rr {
                 Rr(Map s) {}
             }
             
             new Rr(s : "", p : 1)
             """);
  }

  @Test
  public void testRawNamedParam() {
    doTest("""
             
             class Rr {
                 Rr(@NamedParam('s') Map s) {}
             }
             
             new Rr(s : "", <warning>p</warning> : 1)
             """);
  }

  @Test
  public void testNamedDelegateInStaticMethod() {
    doTest("""
             
             class Foo {
                 int aaa
                 boolean bbb
             }
             
             @NamedVariant
             static def bar(@NamedDelegate Foo a) {}
             
             @CompileStatic
             static def foo() {
                 bar(aaa: 10, bbb: true)
             }
             """);
  }

  private void doTest(String before) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GrNamedVariantLabelsInspection());
    if (before.contains("NamedVariant")) before = "import groovy.transform.NamedVariant\n" + before;
    if (before.contains("NamedParam")) before = "import groovy.transform.NamedParam\n" + before;
    if (before.contains("NamedDelegate")) before = "import groovy.transform.NamedDelegate\n" + before;
    if (before.contains("CompileStatic")) before = "import groovy.transform.CompileStatic\n" + before;
    myFixture.configureByText("_.groovy", before);
    myFixture.checkHighlighting();
  }
}
