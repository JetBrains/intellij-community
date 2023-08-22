// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

class GrDefaultBuilderStrategyOnFactoryMethodTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy)
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.<caret>
''', 'builder'
  }

  void 'test custom builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = 'customBuilderMethod')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.<caret>
''', 'customBuilderMethod'
  }

  void 'test null builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = null)
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.<caret>
''', 'builder'
  }

  void 'test empty builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = '')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.<caret>
''', 'builder'
  }

  void 'test spaces builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, builderMethodName = '   ')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.<caret>
''', '   '
  }

  void 'test no prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy)
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }


  void 'test empty prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, prefix = '')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test custom prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, prefix = 'customPrefix')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'build', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter'
  }

  void 'test spaces prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, prefix = '   ')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'build', '   Name', '   Dynamic', '   Counter'
  }

  void 'test null prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, prefix = null)
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test custom build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = 'customBuild')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'customBuild', 'name', 'dynamic', 'counter'
  }

  void 'test null build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = null)
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test empty build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = '')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test spaces build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy, buildMethodName = '    ')
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().<caret>
''', '    ', 'name', 'dynamic', 'counter'
  }

  void 'test next setter'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy)
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().counter(1).<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test one more setter further'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class Pojo {
    @Builder(builderStrategy = DefaultStrategy)
    static Pojo someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().counter(1).name("Janet").<caret>
''', 'build', 'name', 'dynamic', 'counter'
  }

  void 'test other return type'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.DefaultStrategy

class OtherClass { def a,b,c }

class Pojo {
    @Builder(builderStrategy = DefaultStrategy)
    static OtherClass someLongMethodName(def dynamic, int counter, String name) {}
}

Pojo.builder().build().<caret>
''', 'a', 'b', 'c'
  }
}
