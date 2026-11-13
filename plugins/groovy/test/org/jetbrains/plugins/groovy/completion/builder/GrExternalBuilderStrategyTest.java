// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.testFramework.EdtTestUtil;
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
public class GrExternalBuilderStrategyTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void test_no_class() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy)
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, CompletionResult.notContain, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_no_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_empty_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '')
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_custom_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = 'customPrefix')
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "build", "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
  }

  @Test
  public void test_spaces_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '   ')
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "build", "   Name", "   Dynamic", "   Counter");
  }

  @Test
  public void test_null_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = null)
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_custom_build_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = 'customBuild')
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "customBuild", "name", "dynamic", "counter");
  }

  @Test
  public void test_null_build_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = null)
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_empty_build_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '')
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_spaces_build_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '    ')
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, "    ", "name", "dynamic", "counter");
  }

  @Test
  public void test_next_setter() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       @Builder
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
                       class PojoBuilder {}
                       
                       new PojoBuilder().counter(1).<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_one_more_setter_further() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       @Builder
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
                       class PojoBuilder {}
                       
                       new PojoBuilder().counter(1).name("Janet").<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  @Test
  public void test_not_include_super_properties() {
    String code = """
      
      import groovy.transform.builder.Builder
      import groovy.transform.builder.ExternalStrategy
      
      class Pojo {
          String name
          def dynamic
          int counter
      
          def method() {}
      }
      
      class Child extends Pojo {
        String secondName
      }
      
      @Builder(builderStrategy = ExternalStrategy, forClass = Child)
      class PojoBuilder {}
      
      new PojoBuilder().<caret>
      """;
    completion.get().doCompletionTest(code, CompletionResult.contain, "secondName");
    completion.get().doCompletionTest(code, CompletionResult.notContain, "name", "dynamic", "counter");
  }

  @Test
  public void test_include_super_properties() {
    completion.get().doCompletionTest("""
                       
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.ExternalStrategy
                       
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       class Child extends Pojo {
                         String secondName
                       }
                       
                       @Builder(builderStrategy = ExternalStrategy, forClass = Child, includeSuperProperties = true)
                       class PojoBuilder {}
                       
                       new PojoBuilder().<caret>
                       """, CompletionResult.contain, "secondName", "name", "dynamic", "counter");
  }
}
