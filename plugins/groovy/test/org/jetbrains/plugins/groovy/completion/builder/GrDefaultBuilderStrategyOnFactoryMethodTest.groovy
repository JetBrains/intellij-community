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

class GrDefaultBuilderStrategyOnFactoryMethodTest extends GrBuilderTransformationCompletionTestBase {

  void 'test no builder method'() {
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
    doVariantableTest '''\
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
