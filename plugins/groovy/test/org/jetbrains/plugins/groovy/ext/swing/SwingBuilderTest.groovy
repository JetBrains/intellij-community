// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.swing

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PropertyUtil
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.LightProjectTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

@CompileStatic
final class SwingBuilderTest extends LightProjectTest implements ResolveTest {

  @Override
  LightProjectDescriptor getProjectDescriptor() {
    GroovyProjectDescriptors.GROOVY_2_1
  }

  @Test
  void swingBuilderMethod() {
    def method = resolveTest 'new groovy.swing.SwingBuilder().<caret>frame()', PsiMethod
    assert !method.physical
  }

  @Test
  void swingProperty() {
    def method = resolveTest 'new groovy.swing.SwingBuilder().table(<caret>autoscrolls: false)', PsiMethod
    assert PropertyUtil.isSimplePropertySetter(method)
    assert method.containingClass.qualifiedName == "javax.swing.JComponent"
  }
}
