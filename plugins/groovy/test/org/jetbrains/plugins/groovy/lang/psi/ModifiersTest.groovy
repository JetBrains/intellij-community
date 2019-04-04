/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi

import com.intellij.psi.PsiModifier
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase

@CompileStatic
class ModifiersTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  void 'test top level enum is not static'() {
    fixture.addFileToProject 'classes.groovy', 'enum E {}'
    def enumClass = fixture.findClass('E')
    assert !enumClass.hasModifierProperty(PsiModifier.STATIC)
  }

  void 'test inner enum is static'() {
    fixture.addFileToProject 'classes.groovy', 'class Outer { enum E {} }'
    def enumClass = fixture.findClass('Outer.E')
    assert enumClass.hasModifierProperty(PsiModifier.STATIC)
  }
}
