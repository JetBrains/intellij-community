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
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.transformations.GroovyTransformationsBundle

@CompileStatic
class SingletonConstructorInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void setUp() {
    super.setUp()
    fixture.enableInspections(SingletonConstructorInspection)
  }

  void 'test highlighting'() {
    fixture.with {
      configureByText '_.groovy', '''\
@Singleton
class A {
    <error descr="@Singleton class should not have constructors">A</error>() {}
    <error descr="@Singleton class should not have constructors">A</error>(a) {}
}

@Singleton(strict=true)
class ExplicitStrict {
  <error descr="@Singleton class should not have constructors">ExplicitStrict</error>() {}
  <error descr="@Singleton class should not have constructors">ExplicitStrict</error>(a) {}
}

@Singleton(strict = false)
class NonStrict {
  NonStrict() {}
  NonStrict(a) {}
}
'''
      checkHighlighting()
    }
  }

  void 'test make non strict fix'() {
    fixture.with {
      configureByText '_.groovy', '''\
@Singleton
class A {
  <caret>A() {}
}
'''
      def intention = findSingleIntention(GroovyTransformationsBundle.message("singleton.constructor.makeNonStrict"))
      assert intention
      launchAction(intention)
      checkResult '''\
@Singleton(strict = false)
class A {
  <caret>A() {}
}
'''
    }
  }

  void 'test make non strict fix existing'() {
    fixture.with {
      configureByText '_.groovy', '''\
@Singleton(strict = true, property = "lol")
class A {
  <caret>A() {}
}
'''
      def intention = findSingleIntention(GroovyTransformationsBundle.message("singleton.constructor.makeNonStrict"))
      assert intention
      launchAction(intention)
      checkResult '''\
@Singleton(strict = false, property = "lol")
class A {
  <caret>A() {}
}
'''
    }
  }

  void 'test remove constructor fix'() {
    fixture.with {
      configureByText '_.groovy', '''\
@Singleton
class A {
  <caret>A() {}
}
'''
      def intention = findSingleIntention(GroovyTransformationsBundle.message("singleton.constructor.remove"))
      assert intention
      launchAction(intention)
      checkResult '''\
@Singleton
class A {
}
'''
    }
  }
}
