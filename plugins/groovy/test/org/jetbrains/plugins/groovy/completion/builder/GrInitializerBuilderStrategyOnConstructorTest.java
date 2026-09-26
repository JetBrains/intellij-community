// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

public class GrInitializerBuilderStrategyOnConstructorTest extends GrBuilderTransformationCompletionTestBase {
  public void test_no_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  public void test_custom_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = 'customBuilderMethod')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "customBuilderMethod");
  }

  public void test_null_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = null)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  public void test_empty_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = '')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  public void test_spaces_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, builderMethodName = '   ')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.<caret>
                       """, "   ");
  }

  public void test_empty_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = '')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "name", "dynamic", "counter");
  }

  public void test_custom_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = 'customPrefix')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
  }

  public void test_spaces_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = '   ')
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       
                       Pojo.createInitializer().<caret>
                       """, "   Name", "   Dynamic", "   Counter");
  }

  public void test_null_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy, prefix = null)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       
                       Pojo.createInitializer().<caret>
                       """, "name", "dynamic", "counter");
  }

  public void test_next_setter() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Pojo {
                           @Builder(builderStrategy = InitializerStrategy)
                           Pojo(name, dynamic, counter) {}
                       }
                       
                       
                       Pojo.createInitializer().counter(1).<caret>
                       """, "name", "dynamic", "counter");
  }

  public void test_one_more_setter_further() {
    doCompletionTest("""
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
