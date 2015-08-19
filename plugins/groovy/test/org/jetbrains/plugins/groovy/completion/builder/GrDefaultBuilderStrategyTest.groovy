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
class GrDefaultBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {

  void "test no builder method"() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.<caret>
''', 'builder')
  }

  void 'test custom builder method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(builderMethodName = 'customBuilderMethod')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.<caret>
''', 'customBuilderMethod')
  }

  /**
   * The class even won't be compiled.
   * Separate inspection highlights such code and offers to remove the parameter.
   */
  void 'test null builder method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(builderMethodName = null)
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.b<caret>
''', 'builder')
  }

  /**
   * Class will be compiled but there will be runtime error.
   */
  void 'test empty builder method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(builderMethodName = '')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.<caret>
''', 'builder')
  }

  /**
   * I do not know what to say here!
   */
  void 'test spaces builder method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(builderMethodName = '   ')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.<caret>
''', '   ')
  }

  void 'test no prefix'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }


  void 'test empty prefix'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(prefix = '')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }

  void 'test custom prefix'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(prefix = 'customPrefix')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'build', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter')
  }

  void 'test spaces prefix'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(prefix = '   ')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'build', '   Name', '   Dynamic', '   Counter')
  }

  void 'test null prefix'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(prefix = null)
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }

  void 'test custom build method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(buildMethodName = 'customBuild')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'customBuild', 'name', 'dynamic', 'counter')
  }

  void 'test null build method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(buildMethodName = null)
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }

  void 'test empty build method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(buildMethodName = '')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }

  void 'test spaces build method'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder(buildMethodName = '    ')
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().<caret>
''', '    ', 'name', 'dynamic', 'counter')
  }

  void 'test next setter'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().counter(1).<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }

  void 'test one more setter further'() {
    doVariantableTest('''\
import groovy.transform.builder.Builder

@Builder
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}

Pojo.builder().counter(1).name("Janet").<caret>
''', 'build', 'name', 'dynamic', 'counter')
  }
}
