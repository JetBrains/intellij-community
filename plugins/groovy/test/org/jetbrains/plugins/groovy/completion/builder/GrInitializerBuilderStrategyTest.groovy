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

@CompileStatic
class GrInitializerBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no builder method'() {
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
}
