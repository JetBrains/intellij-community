/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.intentions.GrIntentionFixtureKt.grIntentionFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrAliasImportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GrIntentionFixture> grIntention =
    grIntentionFixture(projectFixture, GroovyIntentionsBundle.message("gr.alias.import.intention.name"), null);

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static void foo(){}
      }
      """);
    grIntention.get().doTextTest("""
                 import static Foo.foo
                 
                 fo<caret>o()
                 """, """
                 import static Foo.foo as aliased
                 
                 aliased()
                 """);
  }

  @Test
  public void testOverriden() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static void foo(){}
        static void foo(int x){}
      }
      """);
    grIntention.get().doTextTest("""
                 import static Foo.foo
                 
                 fo<caret>o()
                 foo(2)
                 """, """
                 import static Foo.foo as aliased
                 
                 aliased()
                 aliased(2)
                 """);
  }

  @Test
  public void testOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static void foo(){}
      }
      """);
    grIntention.get().doTextTest("""
                 import static Foo.*
                 
                 fo<caret>o()
                 """, """
                 import static Foo.*
                 import static Foo.foo as aliased
                 
                 aliased()
                 """);
  }

  @Test
  public void testSimpleOnImportStatement() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static void foo(){}
      }
      """);
    grIntention.get().doTextTest("""
                 import static Foo.f<caret>oo
                 
                 foo()
                 """, """
                 import static Foo.foo as aliased
                 
                 aliased()
                 """);
  }

  @Test
  public void testProperty() {
    LightJavaCodeInsightTestFixture myFixture = grIntention.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static def foo = 2
      }
      """);

    grIntention.get().doTextTest("""
                 import static Foo.fo<caret>o
                 
                 print foo
                 print getFoo()
                 print setFoo(2)
                 """, """
                 import static Foo.foo as aliased
                 
                 print aliased
                 print getAliased()
                 print setAliased(2)
                 """);
  }

  @Test
  public void test_on_non_static_import() {
    grIntention.get().doAntiTest("impor<caret>t java.lang.String");
  }

  @Test
  public void test_on_star_import() {
    grIntention.get().doAntiTest("impor<caret>t static java.lang.String.*");
  }

  @Test
  public void test_on_aliased_import() {
    grIntention.get().doAntiTest("impor<caret>t static java.lang.String.valueOf as alreadyAliased");
  }

  @Test
  public void test_on_import_without_reference() {
    grIntention.get().doAntiTest("impor<caret>t static ");
  }

  @Test
  public void test_on_unresolved_import() {
    grIntention.get().doAntiTest("impor<caret>t static foo.bar.Baz");
  }
}
