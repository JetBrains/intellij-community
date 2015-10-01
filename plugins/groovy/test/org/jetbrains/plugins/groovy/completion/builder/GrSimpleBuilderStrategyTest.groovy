/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.completion.builder

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.completion.CompletionResult

@CompileStatic
class GrSimpleBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no prefix'() {
    doVariantableTest('''
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

    doVariantableTest('''
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
    doVariantableTest('''
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
    doVariantableTest '''
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
    doVariantableTest '''
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
    doVariantableTest '''
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
    doVariantableTest '''
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
    doVariantableTest '''
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
    doVariantableTest '''
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
    doVariantableTest '''
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
}
