// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.completion.CompletionResult

@CompileStatic
class GrSimpleBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no prefix'() {
    doCompletionTest('''
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
''', 'setName', 'setDynamic', 'setCounter', 'method')

    doCompletionTest('''
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
''', CompletionResult.notContain, 'build')
  }

  void 'test empty prefix'() {
    doCompletionTest('''
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
''', 'name', 'dynamic', 'counter', 'method')
  }

  void 'test custom prefix'() {
    doCompletionTest '''
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
''', 'customName', 'customDynamic', 'customCounter', 'method'
  }

  void 'test null prefix'() {
    doCompletionTest '''
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
''', 'setName', 'setDynamic', 'setCounter', 'method'
  }

  void 'test spaces prefix'() {
    doCompletionTest '''
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
''', '   Name', '   Dynamic', '   Counter', 'method'
  }

  void 'test next setter'() {
    doCompletionTest '''
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
''', 'setName', 'setDynamic', 'setCounter', 'method'
  }

  void 'test one more setter further'() {
    doCompletionTest '''
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
''', 'setName', 'setDynamic', 'setCounter', 'method'
  }

  void 'test next setter with prefix'() {
    doCompletionTest '''
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
''', 'lolName', 'lolDynamic', 'lolCounter', 'method'
  }

  void 'test one more setter further with prefix'() {
    doCompletionTest '''
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
''', 'lolName', 'lolDynamic', 'lolCounter', 'method'
  }

  void 'test return type with include super'() {
    doCompletionTest('''
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
''', CompletionResult.notContain, 'setDynamic', 'setCounter')
  }
}
