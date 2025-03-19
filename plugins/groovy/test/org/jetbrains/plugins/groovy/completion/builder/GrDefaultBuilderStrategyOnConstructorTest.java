// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

public class GrDefaultBuilderStrategyOnConstructorTest extends GrBuilderTransformationCompletionTestBase {
  public void test_no_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy)
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.<caret>
                       """, "builder");
  }

  public void test_custom_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = 'customBuilderMethod')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.<caret>
                       """, "customBuilderMethod");
  }

  /**
   * The class even won't be compiled.
   * Separate inspection highlights such code and offers to remove the parameter.
   */
  public void test_null_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = null)
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.<caret>
                       """, "builder");
  }

  /**
   * Class will be compiled but there will be runtime error.
   */
  public void test_empty_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = '')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.<caret>
                       """, "builder");
  }

  public void test_spaces_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, builderMethodName = '   ')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.<caret>
                       """, "   ");
  }

  public void test_no_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy)
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_empty_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, prefix = '')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_custom_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, prefix = 'customPrefix')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "build", "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
  }

  public void test_spaces_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, prefix = '   ')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "build", "   Name", "   Dynamic", "   Counter");
  }

  public void test_null_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, prefix = null)
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_custom_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = 'customBuild')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "customBuild", "name", "dynamic", "counter");
  }

  public void test_null_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = null)
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_empty_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = '')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_spaces_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy, buildMethodName = '    ')
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().<caret>
                       """, "    ", "name", "dynamic", "counter");
  }

  public void test_next_setter() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy)
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().counter(1).<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_one_more_setter_further() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.DefaultStrategy
                       
                       class PojoConstructor {
                           @Builder(builderStrategy = DefaultStrategy)
                           PojoConstructor(def dynamic, int counter, String name) {}
                       }
                       
                       PojoConstructor.builder().counter(1).name("Janet").<caret>
                       """, "build", "name", "dynamic", "counter");
  }
}
