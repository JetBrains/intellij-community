// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.completion.CompletionResult

@CompileStatic
class GrExternalBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no class'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy)
class PojoBuilder {}

new PojoBuilder().<caret>
''', CompletionResult.notContain, 'build', 'name', 'dynamic', 'counter'
  }

  void 'test no prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }


  void 'test empty prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test custom prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = 'customPrefix')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter'
  }

  void 'test spaces prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '   ')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', '   Name', '   Dynamic', '   Counter'
  }

  void 'test null prefix'() {
    doCompletionTest('''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = null)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }

  void 'test custom build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = 'customBuild')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'customBuild', 'name', 'dynamic', 'counter'
  }

  void 'test null build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = null)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test empty build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test spaces build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '    ')
class PojoBuilder {}

new PojoBuilder().<caret>
''', '    ', 'name', 'dynamic', 'counter'
  }

  void 'test next setter'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().counter(1).<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test one more setter further'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().counter(1).name("Janet").<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test not include super properties'() {
    String code = '''
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

class Child extends Pojo {
  String secondName
}

@Builder(builderStrategy = ExternalStrategy, forClass = Child)
class PojoBuilder {}

new PojoBuilder().<caret>
'''
    doCompletionTest code, CompletionResult.contain, 'secondName'
    doCompletionTest code, CompletionResult.notContain, 'name', 'dynamic', 'counter'
  }

  void 'test include super properties'() {
    doCompletionTest '''
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

class Child extends Pojo {
  String secondName
}

@Builder(builderStrategy = ExternalStrategy, forClass = Child, includeSuperProperties = true)
class PojoBuilder {}

new PojoBuilder().<caret>
''', CompletionResult.contain, 'secondName', 'name', 'dynamic', 'counter'
  }
}
