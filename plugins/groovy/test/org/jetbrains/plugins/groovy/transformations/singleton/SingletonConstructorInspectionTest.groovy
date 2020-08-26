// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class SingletonConstructorInspectionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

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
      def intention = findSingleIntention(GroovyBundle.message("singleton.constructor.makeNonStrict"))
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
      def intention = findSingleIntention(GroovyBundle.message("singleton.constructor.makeNonStrict"))
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
      def intention = findSingleIntention(GroovyBundle.message("singleton.constructor.remove"))
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
