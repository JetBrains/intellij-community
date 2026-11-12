// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
@TestApplication
public class GrDefaultBuilderStrategyOnFactoryMethodTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @Test
  public void test_no_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy)
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.<caret>
                         """, "builder");
    });
  }

  @Test
  public void test_custom_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = 'customBuilderMethod')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.<caret>
                         """, "customBuilderMethod");
    });
  }

  @Test
  public void test_null_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = null)
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.<caret>
                         """, "builder");
    });
  }

  @Test
  public void test_empty_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = '')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.<caret>
                         """, "builder");
    });
  }

  @Test
  public void test_spaces_builder_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, builderMethodName = '   ')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.<caret>
                         """, "   ");
    });
  }

  @Test
  public void test_no_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy)
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_empty_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, prefix = '')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_custom_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, prefix = 'customPrefix')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "build", "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
    });
  }

  @Test
  public void test_spaces_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, prefix = '   ')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "build", "   Name", "   Dynamic", "   Counter");
    });
  }

  @Test
  public void test_null_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, prefix = null)
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_custom_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = 'customBuild')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "customBuild", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_null_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = null)
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_empty_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = '')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_spaces_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy, buildMethodName = '    ')
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().<caret>
                         """, "    ", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_next_setter() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy)
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().counter(1).<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_one_more_setter_further() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy)
                             static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().counter(1).name("Janet").<caret>
                         """, "build", "name", "dynamic", "counter");
    });
  }

  @Test
  public void test_other_return_type() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.DefaultStrategy
                       
                         class OtherClass { def a,b,c }
                       
                         class Pojo {
                             @Builder(builderStrategy = DefaultStrategy)
                             static OtherClass someLongMethodName(def dynamic, int counter, String name) {}
                         }
                       
                         Pojo.builder().build().<caret>
                         """, "a", "b", "c");
    });
  }
}
