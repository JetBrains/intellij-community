// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

import static com.intellij.openapi.util.RecursionManager.assertOnRecursionPrevention

@CompileStatic
class ResolveClassInNewTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  final String basePath = TestUtils.testDataPath + '/resolve/classInNew'

  @Override
  void setUp() throws Exception {
    super.setUp()
    assertOnRecursionPrevention(testRootDisposable)
  }

  private void doDirectoryTest(String fqn) {
    final name = getTestName()
    fixture.copyDirectoryToProject "$name/src", '/'
    fixture.configureFromTempProjectFile "/somePackage/test.groovy"
    final reference = file.findReferenceAt editor.caretModel.offset
    final resolved = reference.resolve()
    if (fqn == null) {
      assertNull(resolved)
      return
    }
    final clazz = assertInstanceOf(resolved, PsiClass)

    def currentClass = clazz
    def parts = fqn.split('\\$')
    def outerFqn = parts.head()
    def inners = parts.tail().reverse()
    for (part in inners) {
      assertEquals(part, currentClass.name)
      currentClass = currentClass.containingClass
    }
    assertNull(currentClass.containingClass)
    assertEquals(outerFqn, currentClass.qualifiedName)
  }

  // 1. current class & hierarchy with interfaces:
  // class, all interfaces (including superinterfaces), superclass, all its interfaces, ...

  // Current
  // CurrentI, CurrentIOutside

  // CurrentParent
  // CurrentParentI, CurrentParentIOutside
  // (https://issues.apache.org/jira/browse/GROOVY-8358)

  // CurrentParentOutside (https://issues.apache.org/jira/browse/GROOVY-8359)
  // CurrentParentOutsideI

  // 2. outer classes from outermost to current with interfaces but no hierarchy
  // OuterOuter
  // OuterOuterI

  // Outer
  // OuterI, OuterIOutside

  // 3. class in the same file
  // 4. regular (aliased) imports by alias (inverse declaration order)
  // 5. static (aliased) imports by alias (inverse declaration order)
  // 6. class in the same package
  // 7. static (aliased) imports by fieldName: in declaration order (https://issues.apache.org/jira/browse/GROOVY-8361)
  // 8. star imports (declaration order)
  // 9. static star imports (declaration order)
  // 10. default imports

  void 'test inner class of current class'() {
    doDirectoryTest 'somePackage.OuterOuter$Outer$Current$Target'
  }

  void 'test inner class of current interface'() {
    doDirectoryTest 'somePackage.CurrentI$Target'
  }

  void 'test inner class of current interface outside'() {
    doDirectoryTest 'somePackage.CurrentIOutside$Target'
  }

  void 'test inner class of superclass'() {
    doDirectoryTest 'somePackage.CurrentParent$Target'
  }

  void 'test inner class of superclass interface'() {
    doDirectoryTest 'somePackage.CurrentParentI$Target'
  }

  void 'test inner class of superclass interface outside'() {
    doDirectoryTest 'somePackage.CurrentParentIOutside$Target'
  }

  // https://issues.apache.org/jira/browse/GROOVY-8359
  // Groovy fails to resolve such reference unless Target is in the same compilation unit
  // We still resolve it
  void 'test inner class of superclass outside'() {
    doDirectoryTest 'somePackage.CurrentParentOutside$Target'
  }

  void 'test inner class of superclass outside interface'() {
    doDirectoryTest 'somePackage.CurrentParentOutsideI$Target'
  }

  void 'test inner class of outermost class'() {
    doDirectoryTest 'somePackage.OuterOuter$Target'
  }

  void 'test inner class of outermost class interface'() {
    doDirectoryTest 'somePackage.OuterOuterI$Target'
  }

  void 'test inner class of outer class'() {
    doDirectoryTest 'somePackage.OuterOuter$Outer$Target'
  }

  void 'test inner class of outer class interface'() {
    doDirectoryTest 'somePackage.OuterI$Target'
  }

  void 'test inner class of outer class interface outside'() {
    doDirectoryTest 'somePackage.OuterIOutside$Target'
  }

  void 'test class in same file'() {
    doDirectoryTest 'somePackage.Target'
  }

  void 'test class regular import'() {
    doDirectoryTest 'unrelatedPackage.Target'
  }

  void 'test class regular import 2'() {
    doDirectoryTest 'unrelatedPackage.Target2'
  }

  void 'test class static import'() {
    doDirectoryTest 'unrelatedPackage.Container$Target2'
  }

  void 'test class static import 2'() {
    doDirectoryTest 'unrelatedPackage.Container$Target'
  }

  void 'test class in same package'() {
    doDirectoryTest 'somePackage.Target'
  }

  // this test also checks we don't resolve number 7
  // https://issues.apache.org/jira/browse/GROOVY-8361
  void 'test class star import'() {
    doDirectoryTest 'anotherUnrelatedPackage.Target'
  }

  void 'test class star import 2'() {
    doDirectoryTest 'unrelatedPackage.Target'
  }

  void 'test class static star import'() {
    doDirectoryTest 'unrelatedPackage.Container$Target'
  }

  void 'test class static star import 2'() {
    doDirectoryTest 'unrelatedPackage.Container2$Target'
  }

  void 'test do not resolve others'() {
    doDirectoryTest null
  }
}
