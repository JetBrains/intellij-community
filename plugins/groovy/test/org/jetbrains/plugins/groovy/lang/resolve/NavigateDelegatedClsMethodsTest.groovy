// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.util.TestUtils.getAbsoluteTestDataPath

/**
 * @author Max Medvedev
 */
class NavigateDelegatedClsMethodsTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + 'resolve/clsMethod'

  final LightProjectDescriptor projectDescriptor = new GroovyLightProjectDescriptor(TestUtils.mockGroovy2_1LibraryName) {
    @Override
    void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry)

      Library.ModifiableModel gebModel = model.moduleLibraryTable.createLibrary("Geb").modifiableModel
      VirtualFile gebJar = JarFileSystem.instance.refreshAndFindFileByPath(absoluteTestDataPath + 'mockGeb/geb-core-0.7.1.jar!/')
      assert gebJar != null
      gebModel.addRoot(gebJar, OrderRootType.CLASSES)
      VirtualFile gebSource = JarFileSystem.instance.refreshAndFindFileByPath(absoluteTestDataPath + 'mockGeb/geb-core-0.7.1-sources.jar!/')
      assert gebSource != null
      gebModel.addRoot(gebSource, OrderRootType.SOURCES)

      gebModel.commit()
    }
  }

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
      assertEquals('NavigableSupport', (resolved as PsiMethod).containingClass.name)
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
      assertEquals('NavigableSupport', (resolved as PsiMethod).containingClass.name)
    }
  }
}