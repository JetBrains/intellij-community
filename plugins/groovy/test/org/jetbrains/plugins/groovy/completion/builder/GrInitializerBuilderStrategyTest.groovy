// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.completion.CompletionResult

@CompileStatic
class GrInitializerBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no builder method'() {
    doCompletionTest '''\
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
''', 'createInitializer'
  }

  void 'test custom builder method'() {
    doCompletionTest '''\
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
''', 'customBuilderMethod'
  }

  void 'test null builder method'() {
    doCompletionTest '''\
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
''', 'createInitializer'
  }

  void 'test empty builder method'() {
    doCompletionTest '''\
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
''', 'createInitializer'
  }

  void 'test spaces builder method'() {
    doCompletionTest '''\
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
''', '   '
  }

  void 'test empty prefix'() {
    doCompletionTest '''\
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
''', 'name', 'dynamic', 'counter'
  }

  void 'test custom prefix'() {
    doCompletionTest '''\
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
''', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter'
  }

  void 'test spaces prefix'() {
    doCompletionTest '''\
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
''', '   Name', '   Dynamic', '   Counter'
  }

  void 'test null prefix'() {
    doCompletionTest '''\
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
''', 'name', 'dynamic', 'counter'
  }

  void 'test next setter'() {
    doCompletionTest '''\
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
''', 'name', 'dynamic', 'counter'
  }

  void 'test one more setter further'() {
    doCompletionTest '''\
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
''', 'name', 'dynamic', 'counter'
  }

  void 'test include super properties'() {
    doCompletionTest '''
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
''', 'legs', 'color', 'name'
  }

  void 'test not include super properties'() {
    String code = '''
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
'''
    doCompletionTest code, CompletionResult.contain, 'name'
    doCompletionTest code, CompletionResult.notContain, 'color', 'legs'

  }
}
