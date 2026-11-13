// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

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
public class GrDefaultBuilderStrategyOnConstructorTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void test_no_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy)
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.<caret>
                         """, "builder");
    });
  }

  @Test
  public void test_custom_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = 'customBuilderMethod')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.<caret>
                         """, "customBuilderMethod");
    });
  }

  /**
   * The class even won't be compiled.
   * Separate inspection highlights such code and offers to remove the parameter.
   */
  @Test
  public void test_null_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = null)
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.<caret>
                         """, "builder");
    });
  }

  /**
   * Class will be compiled but there will be runtime error.
   */
  @Test
  public void test_empty_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = '')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.<caret>
                         """, "builder");
    });
  }

  @Test
  public void test_spaces_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = '   ')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.<caret>
                         """, "   ");
    });
  }

  @Test
  public void test_no_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy)
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_empty_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, prefix = '')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_custom_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, prefix = 'customPrefix')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "build", "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
    });
  }

  @Test
  public void test_spaces_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, prefix = '   ')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "build", "   Name", "   Dynamic", "   Counter");
    });
  }

  @Test
  public void test_null_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, prefix = null)
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_custom_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = 'customBuild')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "customBuild", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_null_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = null)
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_empty_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = '')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_spaces_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = '    ')
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().<caret>
                         """, "    ", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_next_setter() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy)
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().counter(1).<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_one_more_setter_further() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class PojoConstructor {
                             @Builder(builderStrategy = DefaultStrategy)
                             PojoConstructor(def dynamic, int counter, String name) {}
                         }
                       
                         PojoConstructor.builder().counter(1).name("Janet").<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }
}
