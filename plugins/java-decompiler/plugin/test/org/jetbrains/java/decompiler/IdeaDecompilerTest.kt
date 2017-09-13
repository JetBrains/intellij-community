/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vfs.*
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.io.URLUtil

class IdeaDecompilerTest : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.testDataPath = "${PluginPathManager.getPluginHomePath("java-decompiler")}/plugin/testData"
  }

  override fun tearDown() {
    FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
    for (file in EditorHistoryManager.getInstance(project).files) {
      EditorHistoryManager.getInstance(project).removeFile(file)
    }
    super.tearDown()
  }

  fun testSimple() {
    val file = getTestFile("${PlatformTestUtil.getRtJarPath()}!/java/lang/String.class")
    val decompiled = IdeaDecompiler().getText(file).toString()
    assertTrue(decompiled, decompiled.startsWith("${IdeaDecompiler.BANNER}package java.lang;\n"))
    assertTrue(decompiled, decompiled.contains("public final class String"))
    assertTrue(decompiled, decompiled.contains("@deprecated"))
    assertTrue(decompiled, decompiled.contains("private static class CaseInsensitiveComparator"))
    assertFalse(decompiled, decompiled.contains("/* compiled code */"))
    assertFalse(decompiled, decompiled.contains("synthetic"))
  }

  fun testStubCompatibility() {
    val visitor = MyFileVisitor(psiManager)
    Registry.get("decompiler.dump.original.lines").withValue(true) {
      VfsUtilCore.visitChildrenRecursively(getTestFile("${JavaTestUtil.getJavaTestDataPath()}/psi/cls/mirror"), visitor)
      VfsUtilCore.visitChildrenRecursively(getTestFile("${PlatformTestUtil.getRtJarPath()}!/java/lang"), visitor)
    }
  }

  fun testNavigation() {
    myFixture.openFileInEditor(getTestFile("Navigation.class"))
    doTestNavigation(11, 14, 14, 10)  // to "m2()"
    doTestNavigation(15, 21, 14, 17)  // to "int i"
    doTestNavigation(16, 28, 15, 13)  // to "int r"
  }

  private fun doTestNavigation(line: Int, column: Int, expectedLine: Int, expectedColumn: Int) {
    val target = GotoDeclarationAction.findTargetElement(project, myFixture.editor, offset(line, column)) as Navigatable
    target.navigate(true)
    val expected = offset(expectedLine, expectedColumn)
    assertEquals(expected, myFixture.caretOffset)
  }

  private fun offset(line: Int, column: Int): Int = myFixture.editor.document.getLineStartOffset(line - 1) + column - 1

  fun testHighlighting() {
    myFixture.openFileInEditor(getTestFile("Navigation.class"))
    IdentifierHighlighterPassFactory.doWithHighlightingEnabled {
      myFixture.editor.caretModel.moveToOffset(offset(11, 14))  // m2(): usage, declaration
      assertEquals(2, myFixture.doHighlighting().size)
      myFixture.editor.caretModel.moveToOffset(offset(14, 10))  // m2(): usage, declaration
      assertEquals(2, myFixture.doHighlighting().size)
      myFixture.editor.caretModel.moveToOffset(offset(14, 17))  // int i: usage, declaration
      assertEquals(2, myFixture.doHighlighting().size)
      myFixture.editor.caretModel.moveToOffset(offset(15, 21))  // int i: usage, declaration
      assertEquals(2, myFixture.doHighlighting().size)
      myFixture.editor.caretModel.moveToOffset(offset(15, 13))  // int r: usage, declaration
      assertEquals(2, myFixture.doHighlighting().size)
      myFixture.editor.caretModel.moveToOffset(offset(16, 28))  // int r: usage, declaration
      assertEquals(2, myFixture.doHighlighting().size)
      myFixture.editor.caretModel.moveToOffset(offset(19, 24))  // throws: declaration, m4() call
      assertEquals(2, myFixture.doHighlighting().size)
    }
  }

  fun testLineNumberMapping() {
    Registry.get("decompiler.use.line.mapping").withValue(true) {
      val file = getTestFile("LineNumbers.class")
      assertNull(file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY))

      IdeaDecompiler().getText(file)

      val mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)!!
      assertEquals(11, mapping.bytecodeToSource(3))
      assertEquals(3, mapping.sourceToBytecode(11))
      assertEquals(23, mapping.bytecodeToSource(13))
      assertEquals(13, mapping.sourceToBytecode(23))
      assertEquals(-1, mapping.bytecodeToSource(1000))
      assertEquals(-1, mapping.sourceToBytecode(1000))
    }
  }

  fun testPerformance() {
    val decompiler = IdeaDecompiler()
    val file = getTestFile("${PlatformTestUtil.getRtJarPath()}!/javax/swing/JTable.class")
    PlatformTestUtil.startPerformanceTest("decompiling JTable.class", 10000, { decompiler.getText(file) }).assertTiming()
  }

  fun testStructureView() {
    val file = getTestFile("StructureView.class")
    file.parent.children ; file.parent.refresh(false, true)  // inner classes

    val editor = FileEditorManager.getInstance(project).openFile(file, false)[0]
    val builder = StructureViewBuilder.PROVIDER.getStructureViewBuilder(StdFileTypes.CLASS, file, project)!!
    val viewComponent = builder.createStructureView(editor, project) as StructureViewComponent
    Disposer.register(myFixture.testRootDisposable, viewComponent)
    viewComponent.setActionActive(JavaAnonymousClassesNodeProvider.ID, true)

    val treeStructure = viewComponent.treeStructure
    PlatformTestUtil.updateRecursively(treeStructure.rootElement as AbstractTreeNode<*>)
    PlatformTestUtil.assertTreeStructureEquals(treeStructure, """
      StructureView.java
       StructureView
        B
         B()
         build(int): StructureView
          $1
           class initializer
        StructureView()
        getData(): int
        setData(int): void
        data: int""".trimIndent())
  }


  private fun getTestFile(name: String): VirtualFile {
    val path = if (FileUtil.isAbsolute(name)) name else "${myFixture.testDataPath}/${name}"
    val fs = if (path.contains(URLUtil.JAR_SEPARATOR)) StandardFileSystems.jar() else StandardFileSystems.local()
    return fs.refreshAndFindFileByPath(path)!!
  }

  private fun RegistryValue.withValue(testValue: Boolean, block: () -> Unit) {
    val currentValue = asBoolean()
    try {
      setValue(testValue)
      block()
    }
    finally {
      setValue(currentValue)
    }
  }

  private class MyFileVisitor(private val psiManager: PsiManager) : VirtualFileVisitor<Any>() {
    override fun visitFile(file: VirtualFile): Boolean {
      if (file.isDirectory) {
        println(file.path)
      }
      else if (file.fileType === StdFileTypes.CLASS && !file.name.contains('$')) {
        val clsFile = psiManager.findFile(file)!!
        val mirror = (clsFile as ClsFileImpl).mirror
        val decompiled = mirror.text
        assertTrue(file.path, decompiled.startsWith(IdeaDecompiler.BANNER) || file.name == "package-info.class")

        // check that no mapped line number is on an empty line
        val prefix = "// "
        decompiled.split("\n").dropLastWhile(String::isEmpty).toTypedArray().forEach { s ->
          val pos = s.indexOf(prefix)
          if (pos == 0 && prefix.length < s.length && Character.isDigit(s[prefix.length])) {
            fail("Incorrect line mapping in file " + file.path + " line: " + s)
          }
        }
      }
      else if (ArchiveFileType.INSTANCE == file.fileType) {
        val jarFile = JarFileSystem.getInstance().getRootByLocal(file)
        if (jarFile != null) {
          VfsUtilCore.visitChildrenRecursively(jarFile, this)
        }
      }

      return true
    }
  }
}