/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve

import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor

class GrSyntheticMembersTest extends LightGroovyTestCase {

  final String basePath = ''

  void 'test synthetic members not computed'() {
    def file = myFixture.configureByText('a.groovy', '''
class A {
  def foo
  def bar() {}
}
''') as GroovyFile
    def clazz = file.classes[0] as GrTypeDefinition
    CollectClassMembersUtil.getAllMethods(clazz, false)
    def data = clazz.getUserData(AstTransformContributor.ourTestKey)
    if (data) throw data
  }
}
