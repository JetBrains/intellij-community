// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import groovy.transform.CompileStatic

@CompileStatic
class GrInitializerBuilderStrategyOnConstructorTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy)
    Pojo(name, dynamic, counter) {}
}

Pojo.<caret>
''', 'createInitializer'
  }

  void 'test custom builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, builderMethodName = 'customBuilderMethod')
    Pojo(name, dynamic, counter) {}
}

Pojo.<caret>
''', 'customBuilderMethod'
  }

  void 'test null builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, builderMethodName = null)
    Pojo(name, dynamic, counter) {}
}

Pojo.<caret>
''', 'createInitializer'
  }

  void 'test empty builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, builderMethodName = '')
    Pojo(name, dynamic, counter) {}
}

Pojo.<caret>
''', 'createInitializer'
  }

  void 'test spaces builder method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, builderMethodName = '   ')
    Pojo(name, dynamic, counter) {}
}

Pojo.<caret>
''', '   '
  }

  void 'test empty prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, prefix = '')
    Pojo(name, dynamic, counter) {}
}

Pojo.createInitializer().<caret>
''', 'name', 'dynamic', 'counter'
  }

  void 'test custom prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, prefix = 'customPrefix')
    Pojo(name, dynamic, counter) {}
}

Pojo.createInitializer().<caret>
''', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter'
  }

  void 'test spaces prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, prefix = '   ')
    Pojo(name, dynamic, counter) {}
}


Pojo.createInitializer().<caret>
''', '   Name', '   Dynamic', '   Counter'
  }

  void 'test null prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy, prefix = null)
    Pojo(name, dynamic, counter) {}
}


Pojo.createInitializer().<caret>
''', 'name', 'dynamic', 'counter'
  }

  void 'test next setter'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy)
    Pojo(name, dynamic, counter) {}
}


Pojo.createInitializer().counter(1).<caret>
''', 'name', 'dynamic', 'counter'
  }

  void 'test one more setter further'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy

class Pojo {
    @Builder(builderStrategy = InitializerStrategy)
    Pojo(name, dynamic, counter) {}
}


Pojo.createInitializer().counter(1).name("Janet").<caret>
''', 'name', 'dynamic', 'counter'
  }
}
