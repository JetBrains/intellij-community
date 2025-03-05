// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

public class GrDefaultBuilderStrategyOnFactoryMethodTest extends GrBuilderTransformationCompletionTestBase {
  public void test_no_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy)
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.<caret>
                       """, "builder");
  }

  public void test_custom_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = 'customBuilderMethod')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.<caret>
                       """, "customBuilderMethod");
  }

  public void test_null_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = null)
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.<caret>
                       """, "builder");
  }

  public void test_empty_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = '')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.<caret>
                       """, "builder");
  }

  public void test_spaces_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = '   ')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.<caret>
                       """, "   ");
  }

  public void test_no_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy)
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_empty_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, prefix = '')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_custom_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, prefix = 'customPrefix')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
  }

  public void test_spaces_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, prefix = '   ')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "   Name", "   Dynamic", "   Counter");
  }

  public void test_null_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, prefix = null)
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_custom_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = 'customBuild')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "customBuild", "name", "dynamic", "counter");
  }

  public void test_null_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = null)
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_empty_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = '')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_spaces_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = '    ')
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "    ", "name", "dynamic", "counter");
  }

  public void test_next_setter() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy)
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().counter(1).<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_one_more_setter_further() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy)
                           static Pojo someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().counter(1).name("Janet").<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_other_return_type() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class OtherClass { def a,b,c }
                       
                       class Pojo {
                           @Builder(builderStrategy = DefaultStrategy)
                           static OtherClass someLongMethodName(def dynamic, int counter, String name) {}
                       }
                       
                       Pojo.builder().build().<caret>
                       """, "a", "b", "c");
  }
}
