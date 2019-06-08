// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.geb.GebTestsTest
import org.jetbrains.plugins.groovy.util.TestUtils

class NavigateDelegatedClsMethodsTest extends LightGroovyTestCase {

  final String basePath = TestUtils.testDataPath + 'resolve/clsMethod'

  final LightProjectDescriptor projectDescriptor = GebTestsTest.DESCRIPTOR

  void testNavigationInGroovy() {
    myFixture.with {
      configureByText('A.groovy', '''\
import geb.Page;

class A extends Page {
    void foo() {
        fin<caret>d(".div");
    }
}
''')
      def instance = TargetElementUtil.getInstance()
      def resolved = instance.findTargetElement(editor, instance.allAccepted, editor.caretModel.offset)
      assertInstanceOf resolved, PsiMethod
      assertEquals('Page', (resolved as PsiMethod).containingClass.name)
    }
  }

  void testNavigationInJava() {
    myFixture.with {
      configureByText('A.java', '''\
import geb.Page;

class A extends Page {
    void foo() {
        fin<caret>d(".div");
    }
}
''')
      def instance = TargetElementUtil.getInstance()
      def resolved = instance.findTargetElement(editor, instance.allAccepted, editor.caretModel.offset).navigationElement
      assertInstanceOf resolved, PsiMethod
      assertEquals('Page', (resolved as PsiMethod).containingClass.name)
    }
  }
}