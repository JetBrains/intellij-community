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
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }


  void 'test empty prefix'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test custom prefix'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = 'customPrefix')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'customPrefixName', 'customPrefixDynamic', 'customPrefixCounter', 'customPrefixWithoutField'
  }

  void 'test spaces prefix'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = '   ')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', '   Name', '   Dynamic', '   Counter', '   WithoutField'
  }

  void 'test null prefix'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, prefix = null)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test custom build method'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = 'customBuild')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'customBuild', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test null build method'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = null)
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test empty build method'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '')
class PojoBuilder {}

new PojoBuilder().<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test spaces build method'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo, buildMethodName = '    ')
class PojoBuilder {}

new PojoBuilder().<caret>
''', '    ', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test next setter'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().counter(1).<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test one more setter further'() {
    doVariantableTest '''\
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().counter(1).name("Janet").<caret>
''', 'build', 'name', 'dynamic', 'counter', 'withoutField'
  }

  void 'test only java bean properties'() {
    doVariantableTest '''
import groovy.transform.builder.Builder
import groovy.transform.builder.ExternalStrategy

@Builder(builderStrategy = ExternalStrategy, forClass = Pojo)
class PojoBuilder {}

new PojoBuilder().<caret>
''', CompletionResult.notContain, 'private', 'packageLocal', 'someFieldWithoutSetter'
  }

}
