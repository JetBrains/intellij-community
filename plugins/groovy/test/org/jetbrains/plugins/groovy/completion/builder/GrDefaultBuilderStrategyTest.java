// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import org.jetbrains.plugins.groovy.completion.CompletionResult;

public class GrDefaultBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {
  public void test_no_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "builder");
  }

  public void test_custom_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(builderMethodName = 'customBuilderMethod')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "customBuilderMethod");
  }

  /**
   * The class even won't be compiled.
   * Separate inspection highlights such code and offers to remove the parameter.
   */
  public void test_null_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(builderMethodName = null)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.b<caret>
                       """, "builder");
  }

  /**
   * Class will be compiled but there will be runtime error.
   */
  public void test_empty_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(builderMethodName = '')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "builder");
  }

  /**
   * I do not know what to say here!
   */
  public void test_spaces_builder_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(builderMethodName = '   ')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.<caret>
                       """, "   ");
  }

  public void test_no_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_empty_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(prefix = '')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_custom_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(prefix = 'customPrefix')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "customPrefixName", "customPrefixDynamic", "customPrefixCounter");
  }

  public void test_spaces_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(prefix = '   ')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "   Name", "   Dynamic", "   Counter");
  }

  public void test_null_prefix() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(prefix = null)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_custom_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(buildMethodName = 'customBuild')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "customBuild", "name", "dynamic", "counter");
  }

  public void test_null_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(buildMethodName = null)
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_empty_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(buildMethodName = '')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_spaces_build_method() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder(buildMethodName = '    ')
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().<caret>
                       """, "    ", "name", "dynamic", "counter");
  }

  public void test_next_setter() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().counter(1).<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_one_more_setter_further() {
    doCompletionTest("""
                       import groovy.transform.builder.Builder
                       
                       @Builder
                       class Pojo {
                           String name
                           def dynamic
                           int counter
                       
                           def method() {}
                       }
                       
                       Pojo.builder().counter(1).name("Janet").<caret>
                       """, "build", "name", "dynamic", "counter");
  }

  public void test_include_super_properties() {
    doCompletionTest("""
                       
                       import groovy.transform.builder.Builder
                       class Animal {
                           String color
                           int legs
                       }
                       
                       @Builder(includeSuperProperties = true)
                       class Pet extends Animal{
                           String name
                       }
                       
                       new Pet().builder().color("Grey").<caret>
                       """, "legs", "name");
  }

  public void test_include_super_properties_2() {
    doCompletionTest("""
                       
                       import groovy.transform.builder.Builder
                       class Animal {
                           String color
                           int legs
                       }
                       
                       @Builder(includeSuperProperties = true)
                       class Pet extends Animal{
                           String name
                       }
                       
                       new Pet().builder().name("Janet").<caret>
                       """, "color", "legs");
  }

  public void test_not_include_super_properties() {
    String code = """
      
      import groovy.transform.builder.Builder
      class Animal {
          String color
          int legs
      }
      
      @Builder
      class Pet extends Animal{
          String name
      }
      
      new Pet().builder().<caret>
      """;
    doCompletionTest(code, CompletionResult.contain, "name");
    doCompletionTest(code, CompletionResult.notContain, "color", "legs");
  }
}
