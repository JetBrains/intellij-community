// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.dgm

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase

@CompileStatic
class ResolveDGMMethod23Test extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_2_3

  void testIsNumber() {
    def resolved = resolveByText('"1.2.3".isN<caret>umber()', GrGdkMethod)
    assert resolved.staticMethod.containingClass.qualifiedName == 'org.codehaus.groovy.runtime.StringGroovyMethods'
  }

  void testSort() {
    def resolved = resolveByText('[].so<caret>rt()', GrGdkMethod)
    def parameterList = resolved.staticMethod.parameterList.parameters
    assert parameterList.size() == 1
    assert parameterList[0].type.canonicalText == 'java.lang.Iterable<T>'
  }

  // TODO groovy-all-2.3.0.jar was stripped to run on java 6. Unignore when it will contain NioGroovyMethods
  @SuppressWarnings("GroovyUnusedDeclaration")
  void _testCloseable() {
    myFixture.addClass('package java.io; class Closeable {}')
    def resolved = resolveByText('''\
class A implements Closeable {}
new A().withC<caret>loseable {}
''', GrGdkMethod)
    assert resolved.staticMethod.containingClass.qualifiedName == 'org.codehaus.groovy.runtime.NioGroovyMethods'
  }
}
