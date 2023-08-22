// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import groovy.transform.CompileStatic

@CompileStatic
class GrDefaultBuilderStrategyOnConstructorTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy)
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.<caret>
''', 'builder'
  }

  void 'test custom builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = 'customBuilderMethod')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.<caret>
''', 'customBuilderMethod'
  }

  /**
   * The class even won't be compiled.
   * Separate inspection highlights such code and offers to remove the parameter.
   */
  void 'test null builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = null)
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.<caret>
''', 'builder'
  }

  /**
   * Class will be compiled but there will be runtime error.
   */
  void 'test empty builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = '')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.<caret>
''', 'builder'
  }

  void 'test spaces builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = '   ')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.<caret>
''', '   '
  }

  void 'test no prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy)
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }


  void 'test empty prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, prefix = '')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test custom prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, prefix = 'customPrefix')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'build', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter'
  }

  void 'test spaces prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, prefix = '   ')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'build', '   Name', '   Dynamic', '   Counter'
  }

  void 'test null prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, prefix = null)
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test custom build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = 'customBuild')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'customBuild', 'name', 'dynamic', 'counter'
  }

  void 'test null build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = null)
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test empty build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = '')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test spaces build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = '    ')
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().<caret>
''', '    ', 'name', 'dynamic', 'counter'
  }

  void 'test next setter'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy)
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().counter(1).<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test one more setter further'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class PojoConstructor {
    @Builder(builderStrategy = DefaultStrategy)
    PojoConstructor(def dynamic, int counter, String name) {}
}

PojoConstructor.builder().counter(1).name("Janet").<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }
}
