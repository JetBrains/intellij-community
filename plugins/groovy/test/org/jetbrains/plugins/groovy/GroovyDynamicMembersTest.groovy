// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.After
import org.junit.Before
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING

@CompileStatic
class GroovyDynamicMembersTest extends GroovyLatestTest implements ResolveTest {

  @Before
  void 'init state'() {
    def methodDescriptor = new DynamicElementSettings().with {
      containingClassName = JAVA_LANG_STRING
      name = 'foo'
      type = 'void'
      method = true
      params = [] // Collections.emptyList() // https://issues.apache.org/jira/browse/GROOVY-8961
      it
    }
    def propertyDescriptor = new DynamicElementSettings().with {
      containingClassName = JAVA_LANG_STRING
      name = 'foo'
      it
    }
    DynamicManager.getInstance(project).with {
      addMethod(methodDescriptor)
      addProperty(propertyDescriptor)
    }
  }

  @After
  void 'clear state'() {
    DynamicManager.getInstance(project).loadState(new DRootElement())
  }

  @Test
  void 'resolve to dynamic method'() {
    resolveTest '"hi".<caret>foo()', PsiMethod
  }

  @Test
  void 'resolve to dynamic property'() {
    resolveTest '"hi".<caret>foo', PsiField
  }
}
