// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
@TestApplication
public class GrInitializerBuilderStrategyOnConstructorTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void test_no_builder_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  @Test
  public void test_custom_builder_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = 'customBuilderMethod')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "customBuilderMethod");
  }

  @Test
  public void test_null_builder_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = null)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  @Test
  public void test_empty_builder_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = '')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  @Test
  public void test_spaces_builder_method() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = '   ')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "   ");
  }

  @Test
  public void test_empty_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = '')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "name", "dynamic", "counter");
  }

  @Test
  public void test_custom_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = 'customPrefix')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
  }

  @Test
  public void test_spaces_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = '   ')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       
                       Pojo.createInitializer().<caret>
                       """, "   Name", "   Dynamic", "   Counter");
  }

  @Test
  public void test_null_prefix() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = null)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       
                       Pojo.createInitializer().<caret>
                       """, "name", "dynamic", "counter");
  }

  @Test
  public void test_next_setter() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       
                       Pojo.createInitializer().counter(1).<caret>
                       """, "name", "dynamic", "counter");
  }

  @Test
  public void test_one_more_setter_further() {
    completion.get().doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       
                       Pojo.createInitializer().counter(1).name("Janet").<caret>
                       """, "name", "dynamic", "counter");
  }
}
