// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.completion.CompletionResult

@CompileStatic
class GrDefaultBuilderStrategyTest extends GrBuilderTransformationCompletionTestBase {

  void "test no builder method"() {
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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
    doCompletionTest('''\
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

  void 'test include super properties'() {
    doCompletionTest '''
import groovy.transform.builder.Builder
class Animal {
    String color
    int legs
}

@Builder(includeSuperProperties = true)
class Pet extends Animal{
    String name
}

new Pet().builder().color("Grey").<caret>
''', 'legs', 'name'
  }

  void 'test include super properties 2'() {
    doCompletionTest '''
import groovy.transform.builder.Builder
class Animal {
    String color
    int legs
}

@Builder(includeSuperProperties = true)
class Pet extends Animal{
    String name
}

new Pet().builder().name("Janet").<caret>
''', 'color', 'legs'
  }

  void 'test not include super properties'() {
     String code = '''
import groovy.transform.builder.Builder
class Animal {
    String color
    int legs
}

@Builder
class Pet extends Animal{
    String name
}

new Pet().builder().<caret>
'''
    doCompletionTest code, CompletionResult.contain, 'name'
    doCompletionTest code, CompletionResult.notContain, 'color', 'legs'
  }
}
