// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

import static org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.getStringArrayValue
import static org.jetbrains.plugins.groovy.util.TestUtils.disableAstLoading

@CompileStatic
class ConstantExpressionTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  private void addJavaAnnotation() {
    fixture.addClass '''\
package com.foo;

public @interface MyJavaAnnotation {
    String[] stringArrayValue() default {};
    String stringValue() default "default string";
}
'''
  }

  private void addJavaConstants() {
    fixture.addClass '''\
package com.foo;

public interface Constants {
  String HELLO = "java hello";
  String WORLD = "java world";
  String COMPOUND = HELLO + " " + WORLD;
}
'''
  }

  void 'test annotation value from java'() {
    addJavaAnnotation()
    addJavaConstants()
    fixture.addFileToProject '_.groovy', '''\
import com.foo.Constants
import com.foo.MyJavaAnnotation

@MyJavaAnnotation(stringArrayValue = [
        Constants.HELLO,
        Constants.WORLD,
        Constants.COMPOUND,
        "literal"
])
class GroovyClass {}
'''
    disableAstLoading project, testRootDisposable
    def clazz = fixture.findClass 'GroovyClass'
    def annotation = clazz.modifierList.findAnnotation 'com.foo.MyJavaAnnotation'
    def values = getStringArrayValue annotation, 'stringArrayValue', false
    assertOrderedEquals(["java hello", "java world", "java hello java world", "literal"], values)
  }
}
