// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import org.jetbrains.plugins.groovy.completion.CompletionResult;

public class GrInitializerBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {
  public void test_no_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  public void test_custom_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, builderMethodName = 'customBuilderMethod')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "customBuilderMethod");
  }

  public void test_null_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, builderMethodName = null)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  public void test_empty_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, builderMethodName = '')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "createInitializer");
  }

  public void test_spaces_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, builderMethodName = '   ')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "   ");
  }

  public void test_empty_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, prefix = '')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "name", "dynamic", "counter");
  }

  public void test_custom_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, prefix = 'customPrefix')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
  }

  public void test_spaces_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, prefix = '   ')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "   Name", "   Dynamic", "   Counter");
  }

  public void test_null_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy, prefix = null)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.createInitializer().<caret>
                       """, "name", "dynamic", "counter");
  }

  public void test_next_setter() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.createInitializer().counter(1).<caret>
                       """, "name", "dynamic", "counter");
  }

  public void test_one_more_setter_further() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       @Builder(builderStrategy = InitializerStrategy)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.createInitializer().counter(1).name("Janet").<caret>
                       """, "name", "dynamic", "counter");
  }

  public void test_include_super_properties() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       import groovy.transform.builder.InitializerStrategy
                       
                       class Animal {
                           String color
                           int legs
                       }
                       
                       @Builder(includeSuperProperties = true, builderStrategy = InitializerStrategy)
                       class Pet extends Animal{
                           String name
                       }
                       
                       Pet.createInitializer().<caret>
                       """, "legs", "color", "name");
  }

  public void test_not_include_super_properties() {
    String code = """
      import groovy.transform.builder.Builder
      import groovy.transform.builder.InitializerStrategy
      
      class Animal {
          String color
          int legs
      }
      
      @Builder(builderStrategy = InitializerStrategy)
      class Pet extends Animal{
          String name
      }
      
      Pet.createInitializer().<caret>
      """;
    doCompletionTest(code, CompletionResult.contain, "name");
    doCompletionTest(code, CompletionResult.notContain, "color", "legs");
  }
}
