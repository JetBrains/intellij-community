/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.move

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class GroovyMoveClassToInnerTest extends GroovyMoveTestBase {
  private String[] myConflicts = null

  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}refactoring/move/moveClassToInner/"
  }

  void testContextChange1() {
    doTest("pack2.A", "pack1.Class1")
  }

  void testContextChange2() {
    doTest("pack2.A", "pack1.Class1")
  }

  void testInnerImport() throws Exception {
    doTest("pack2.A", "pack1.Class1")
  }

  void testInsertInnerClassImport() throws Exception {
    final settings = CodeStyleSettingsManager.getSettings(myFixture.project).getCustomSettings(GroovyCodeStyleSettings.class)
    def oldValue = settings.INSERT_INNER_CLASS_IMPORTS
    settings.INSERT_INNER_CLASS_IMPORTS = true
    try {
      doTest("pack2.A", "pack1.Class1")
    }
    finally {
      settings.INSERT_INNER_CLASS_IMPORTS = oldValue
    }
  }

  void testSimultaneousMove() throws Exception {
    final settings = CodeStyleSettingsManager.instance.currentSettings.getCustomSettings(GroovyCodeStyleSettings.class)
    final oldValue = settings.INSERT_INNER_CLASS_IMPORTS
    settings.INSERT_INNER_CLASS_IMPORTS = false
    try {
      doTest("pack2.A", "pack1.Class1", "pack0.Class0")
    }
    finally {
      settings.INSERT_INNER_CLASS_IMPORTS = oldValue
    }
  }

  void testMoveMultiple1() throws Exception {
    doTest("pack2.A", "pack1.Class1", "pack1.Class2")
  }

  void testRefToInner() throws Exception {
    doTest("pack2.A", "pack1.Class1")
  }

  void testRefToConstructor() throws Exception {
    doTest("pack2.A", "pack1.Class1")
  }

  void testSecondaryClass() throws Exception {
    doTest("pack1.User", "pack1.Class2")
  }

  void testStringsAndComments() throws Exception {
    doTest("pack1.A", "pack1.Class1")
  }

  void testStringsAndComments2() throws Exception {
    doTest("pack1.A", "pack1.Class1")
  }

  void testNonJava() throws Exception {
    doTest("pack1.A", "pack1.Class1")
  }

  void testLocallyUsedPackageLocalToPublicInterface() throws Exception {
    doTest("pack2.A", "pack1.Class1")
  }

  void _testPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Field <b><code>Class1.c2</code></b> uses a package-private class <b><code>pack1.Class2</code></b>.")
  }

  void _testMoveIntoPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>")
  }

  void _testMoveOfPackageLocalClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>Class1</code></b> will no longer be accessible from field <b><code>Class2.c1</code></b>")
  }

  void testMoveIntoPrivateInnerClass() throws Exception {
    doTestConflicts("pack1.Class1", "pack1.A.PrivateInner", "Class <b><code>Class1</code></b> will no longer be accessible from class <b><code>pack1.Class2</code></b>")
  }

  void _testMoveWithPackageLocalMember() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Method <b><code>Class1.doStuff()</code></b> will no longer be accessible from method <b><code>Class2.test()</code></b>")
  }

  void testDuplicateInner() throws Exception {
    doTestConflicts("pack1.Class1", "pack2.A", "Class <b><code>pack2.A</code></b> already contains an inner class named <b><code>Class1</code></b>")
  }

  private void doTestConflicts(String className, String targetClassName, String... expectedConflicts) {
    try {
      myConflicts = expectedConflicts
      doTest(targetClassName, className)
    }
    finally {
      myConflicts = null
    }
  }

  @Override
  boolean perform(VirtualFile root, String moveTo, String... names) {
    final PsiClass[] classes = new PsiClass[names.length]
    for (int i = 0; i < classes.length; i++) {
      String className = names[i]
      classes[i] = myFixture.findClass(className)
      assertNotNull("Class $className not found", classes[i])
    }

    PsiClass targetClass = myFixture.findClass(moveTo)
    assertNotNull(targetClass)

    def processor = new MoveClassToInnerProcessor(myFixture.project, classes, targetClass, true, true, null)
    if (myConflicts != null) {
      def usages = processor.findUsages()
      def conflicts = processor.getConflicts(usages)
      assertSameElements(conflicts.values(), myConflicts)
      return false
    }
    else {
      processor.run()
      PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
      FileDocumentManager.instance.saveAllDocuments()
      return true
    }
  }
}
