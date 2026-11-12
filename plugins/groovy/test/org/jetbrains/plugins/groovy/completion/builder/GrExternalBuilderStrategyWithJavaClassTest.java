// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.completion.CompletionResult;
import org.jetbrains.plugins.groovy.completion.GroovyCompletionTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.completion.GroovyCompletionFixture.groovyCompletionFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GrExternalBuilderStrategyWithJavaClassTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyCompletionTestFixture> completion = groovyCompletionFixture(groovy);

  @BeforeEach
  public void setUp() {
    runInEdtAndWait(() -> {
      groovy.get().addClass("""
                           public class Pojo {
                               String name;
                               Object dynamic;
                               int counter;
                               public Object someFieldWithoutSetter;
                         
                               public void setName(String name) {}
                               public void setDynamic(Object dynamic) {}
                               public void setCounter(int counter) {}
                               public void setWithoutField(int a) {}
                               private void setPrivate(Object a) {}
                               void setPackageLocal(Object a) {}
                           }
                           """);
    });
  }

  @Test
  public void test_no_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "build", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_empty_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '')
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "build", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_custom_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = 'customPrefix')
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "build", "customPrefixName", "customPrefixDynamic", "customPrefixCounter", "customPrefixWithoutField");
    });
  }

  @Test
  public void test_spaces_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '   ')
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "build", "   Name", "   Dynamic", "   Counter", "   WithoutField");
    });
  }

  @Test
  public void test_null_prefix() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = null)
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "build", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_custom_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = 'customBuild')
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "customBuild", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_null_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = null)
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "build", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_empty_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '')
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "build", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_spaces_build_method() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '    ')
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, "    ", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_next_setter() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
                         class PojoBuilder {}
                       
                         new PojoBuilder().counter(1).<caret>
                         """, "build", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_one_more_setter_further() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
                         class PojoBuilder {}
                       
                         new PojoBuilder().counter(1).name("Janet").<caret>
                         """, "build", "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_only_java_bean_properties() {
    runInEdtAndWait(() -> {
      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, CompletionResult.notContain, "private", "packageLocal", "someFieldWithoutSetter");
    });
  }

  @Test
  public void test_not_include_super_properties() {
    runInEdtAndWait(() -> {
      groovy.get().addClass("""
                           class Child extends Pojo {
                             String secondName;
                             public void setSecondName(String secondName) {}
                           }
                           """);

      String code = """
        import groovy.transform.builder.Builder
        import groovy.transform.builder.ExternalStrategy
      
        @Builder(builderStrategy = ExternalStrategy, forClass = Child)
        class PojoBuilder {}
      
        new PojoBuilder().<caret>
        """;
      completion.get().doCompletionTest(code, CompletionResult.contain, "secondName");
      completion.get().doCompletionTest(code, CompletionResult.notContain, "name", "dynamic", "counter", "withoutField");
    });
  }

  @Test
  public void test_include_super_properties() {
    runInEdtAndWait(() -> {
      groovy.get().addClass("""
                           class Child extends Pojo {
                             String secondName;
                             public void setSecondName(String secondName) {}
                           }
                           """);

      completion.get().doCompletionTest("""
                         import groovy.transform.builder.Builder
                         import groovy.transform.builder.ExternalStrategy
                       
                         @Builder(builderStrategy = ExternalStrategy, forClass = Child, includeSuperProperties = true)
                         class PojoBuilder {}
                       
                         new PojoBuilder().<caret>
                         """, CompletionResult.contain, "secondName", "name", "dynamic", "counter", "withoutField");
    });
  }
}
