// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion.builder

import org.jetbrains.plugins.groovy.completion.CompletionResult

class GrExternalBuilderStrategyWithJavaClassTest extends GrBuilderTransformationCompletionTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myFixture.addClass('''
public class Pojo {
    String name;
    Object dynamic;
    int counter;
    public Object someFieldWithoutSetter;

    public void setName(String name) {}
    public void setDynamic(Object dynamic) {}
    public void setCounter(int counter) {}
    public void setWithoutField(int a) {}
    private void setPrivate(Object a) {}
    void setPackageLocal(Object a) {}
}
''')
  }

  void 'test no prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }


  void 'test empty prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test custom prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = 'customPrefix')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter', 'customPrefixWithoutField'
  }

  void 'test spaces prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '   ')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', '   Name', '   Dynamic', '   Counter', '   WithoutField'
  }

  void 'test null prefix'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = null)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test custom build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = 'customBuild')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'customBuild', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test null build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = null)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test empty build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test spaces build method'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '    ')
class PojoBuilder {}

new PojoBuilder().<caret>
''', '    ', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test next setter'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().counter(1).<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test one more setter further'() {
    doCompletionTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().counter(1).name("Janet").<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test only java bean properties'() {
    doCompletionTest '''
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().<caret>
''', CompletionResult.notContain, 'private', 'packageLocal', 'someFieldWithoutSetter'
  }

  void 'test not include super properties'() {
    myFixture.addClass('''
class Child extends Pojo {
  String secondName;
  public void setSecondName(String secondName) {}
}
''')

    String code = '''
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Child)
class PojoBuilder {}

new PojoBuilder().<caret>
'''
    doCompletionTest code, CompletionResult.contain, 'secondName'
    doCompletionTest code, CompletionResult.notContain, 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test include super properties'() {
    myFixture.addClass('''
class Child extends Pojo {
  String secondName;
  public void setSecondName(String secondName) {}
}
''')

    doCompletionTest '''
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Child, includeSuperProperties = true)
class PojoBuilder {}

new PojoBuilder().<caret>
''', CompletionResult.contain, 'secondName', 'name', 'dynamic', 'counter', 'withoutField'
  }

}
