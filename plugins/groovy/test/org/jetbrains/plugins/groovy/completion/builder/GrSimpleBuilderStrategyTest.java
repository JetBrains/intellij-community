// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import org.jetbrains.plugins.groovy.completion.CompletionResult;

public class GrSimpleBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {
  public void test_no_prefix() {
    doCompletionTest("""
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

    doCompletionTest("""
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
  }

  public void test_empty_prefix() {
    doCompletionTest("""
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
  }

  public void test_custom_prefix() {
    doCompletionTest("""
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
  }

  public void test_null_prefix() {
    doCompletionTest("""
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
  }

  public void test_spaces_prefix() {
    doCompletionTest("""
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
  }

  public void test_next_setter() {
    doCompletionTest("""
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
  }

  public void test_one_more_setter_further() {
    doCompletionTest("""
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
  }

  public void test_next_setter_with_prefix() {
    doCompletionTest("""
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
  }

  public void test_one_more_setter_further_with_prefix() {
    doCompletionTest("""
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
  }

  public void test_return_type_with_include_super() {
    doCompletionTest("""
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
  }
}
