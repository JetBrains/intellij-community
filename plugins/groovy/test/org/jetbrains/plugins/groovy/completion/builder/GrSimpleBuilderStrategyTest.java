// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.completion.CompletionResult;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GrSimpleBuilderStrategyTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void test_no_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy)
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().<caret>
                         """, "setName", "setDynamic", "setCounter", "method");

      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy)
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().<caret>
                         """, CompletionResult.notContain, "build");
    });
  }

  @Test
  public void test_empty_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy, prefix = '')
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().<caret>
                         """, "name", "dynamic", "counter", "method");
    });
  }

  @Test
  public void test_custom_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy, prefix = 'custom')
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().<caret>
                         """, "customName", "customDynamic", "customCounter", "method");
    });
  }

  @Test
  public void test_null_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy, prefix = null)
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().<caret>
                         """, "setName", "setDynamic", "setCounter", "method");
    });
  }

  @Test
  public void test_spaces_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy, prefix = '   ')
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().<caret>
                         """, "   Name", "   Dynamic", "   Counter", "method");
    });
  }

  @Test
  public void test_next_setter() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy)
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().setName("Janet").<caret>
                         """, "setName", "setDynamic", "setCounter", "method");
    });
  }

  @Test
  public void test_one_more_setter_further() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy)
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().setName("Janet").setCounter(35).<caret>
                         """, "setName", "setDynamic", "setCounter", "method");
    });
  }

  @Test
  public void test_next_setter_with_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy, prefix = 'lol')
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().lolName("Janet").<caret>
                         """, "lolName", "lolDynamic", "lolCounter", "method");
    });
  }

  @Test
  public void test_one_more_setter_further_with_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy, prefix = 'lol')
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().lolName("Janet").lolCounter(35).<caret>
                         """, "lolName", "lolDynamic", "lolCounter", "method");
    });
  }

  @Test
  public void test_return_type_with_include_super() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.SimpleStrategy
                       
                         @Builder(builderStrategy = SimpleStrategy, includeSuperProperties = true)
                         class Pojo {
                             String name
                             def dynamic
                             int counter
                       
                             def method() {}
                         }
                       
                         new Pojo().setName().<caret>
                         """, CompletionResult.notContain, "setDynamic", "setCounter");
    });
  }
}
