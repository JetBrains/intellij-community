// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.dgm

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase

@CompileStatic
class ResolveDGMMethodLatestTest extends GroovyResolveTestCase {

  LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void testCloseable() {
    myFixture.addClass('package java.io; class Closeable {}')
    def resolved = resolveByText('''\
class A implements Closeable {}
new A().withC<caret>loseable {}
''', GrGdkMethod)
    assert resolved.staticMethod.containingClass.qualifiedName == 'org.codehaus.groovy.runtime.IOGroovyMethods'
  }
}