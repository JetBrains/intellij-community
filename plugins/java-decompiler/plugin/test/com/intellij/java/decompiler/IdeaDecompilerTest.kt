// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.decompiler

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider
import com.intellij.ide.structureView.impl.java.KindSorter
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.withValue
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.java.decompiler.DecompilerPreset
import org.jetbrains.java.decompiler.IDEA_DECOMPILER_BANNER
import org.jetbrains.java.decompiler.IdeaDecompiler
import org.jetbrains.java.decompiler.IdeaDecompilerSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes

@TestApplication
class IdeaDecompilerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
    private val LOG = logger<IdeaDecompilerTest>()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//plugins/java-decompiler/plugin:testData")
      BazelTestUtil.getFileFromBazelRuntime(label).absolutePathString()
    }
    else {
      "${PluginPathManager.getPluginHomePath("java-decompiler")}/plugin/testData"
    }
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    withContext(Dispatchers.EDT) { FileEditorManagerEx.getInstanceEx(myFixture.project).closeAllFiles() }
    EditorHistoryManager.getInstance(myFixture.project).removeAllFiles()

    val defaultState = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
    IdeaDecompilerSettings.getInstance().loadState(defaultState)
  }

  @Test
  fun testSimple() {
    val file = getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang/String.class")
    val decompiled = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { IdeaDecompiler().getText(file).toString() }
    assertTrue(decompiled.startsWith("${IDEA_DECOMPILER_BANNER}package java.lang;\n"), decompiled)
    assertTrue(decompiled.contains("public final class String"), decompiled)
    assertTrue(decompiled.contains("@deprecated"), decompiled)
    assertTrue(decompiled.contains("private static class CaseInsensitiveComparator"), decompiled)
    assertFalse(decompiled.contains("/* compiled code */"), decompiled)
    assertFalse(decompiled.contains("synthetic"), decompiled)
    assertFalse(decompiled.contains("Limits for direct nodes are exceeded"), decompiled)
    assertFalse(decompiled.contains("Limits for variable nodes are exceeded"), decompiled)
  }

  @Test
  fun testSimpleWithNodeLimit() {
    val advancedSetting = "decompiler.max.direct.nodes.count"
    val previousCount = AdvancedSettings.getInt(advancedSetting)
    try {
      AdvancedSettings.setInt(advancedSetting, 5)
      val file = getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang/String.class")
      val decompiled = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { IdeaDecompiler().getText(file).toString() }
      assertTrue(decompiled.contains("Limits for direct nodes are exceeded"), decompiled)
      //small methods are decompiled normally
      assertTrue(decompiled.contains("""
          public String(char[] var1) {
                  this.value = Arrays.copyOf(var1, var1.length);
              }""".trimIndent()), decompiled)
    }
    finally {
      AdvancedSettings.setInt(advancedSetting, previousCount)
    }
  }

  @Test
  fun testSimpleWithVariableLimit() {
    val advancedSetting = "decompiler.max.variable.nodes.count"
    val previousCount = AdvancedSettings.getInt(advancedSetting)
    try {
      AdvancedSettings.setInt(advancedSetting, 5)
      val file = getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang/String.class")
      val decompiled = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { IdeaDecompiler().getText(file).toString() }
      assertTrue(decompiled.contains("Limits for variable nodes are exceeded"), decompiled)
      //small methods are decompiled normally
      assertTrue(decompiled.contains(
        """
        public char[] toCharArray() {
                char[] var1 = new char[this.value.length];
                System.arraycopy(this.value, 0, var1, 0, this.value.length);
                return var1;
            }""".trimIndent()), decompiled)
    }
    finally {
      AdvancedSettings.setInt(advancedSetting, previousCount)
    }
  }

  @Test
  fun testStubCompatibility(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val classesPath = if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//plugins/java-decompiler/engine:testData")
      BazelTestUtil.getFileFromBazelRuntime(label).resolve("manual/classes").absolutePathString()
    }
    else {
      "${PluginPathManager.getPluginHomePath("java-decompiler")}/engine/testData/manual/classes"
    }
    val visitor = MyFileVisitor(PsiManager.getInstance(myFixture.project))
    val roots = listOf(
      getTestFile("${JavaTestUtil.getJavaTestDataPath()}/psi/cls/mirror"),
      getTestFile(classesPath),
      getTestFile("${IdeaTestUtil.getMockJdk18Path().path}/jre/lib/rt.jar!/java/lang"),
    )
    readActionBlocking {
      Registry.get("decompiler.dump.original.lines").withValue(true) {
        for (root in roots) {
          VfsUtilCore.visitChildrenRecursively(root, visitor)
        }
      }
    }
  }

  @Test
  fun testSignatureWithMoreInterfacesThanClassFile(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestInterfaceSignatureMismatch("MoreInterfaces", "Runnable", "Serializable") }

  @Test
  fun testSignatureWithFewerInterfacesThanClassFile(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTestInterfaceSignatureMismatch("FewerInterfaces", "Runnable") }

  private suspend fun doTestInterfaceSignatureMismatch(name: String, vararg interfaces: String) {
    val file = getTestFile("${JavaTestUtil.getJavaTestDataPath()}/psi/cls/mirror/pkg/$name.class")
    readAction {
      val psiFile = PsiManager.getInstance(myFixture.project).findFile(file) as ClsFileImpl
      val decompiled = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { psiFile.mirror.text }
      assertTrue(decompiled.contains("implements ${interfaces.joinToString(", ")} {"), decompiled)
      val psiClass = psiFile.classes.single()
      assertEquals(interfaces.size, psiClass.implementsListTypes.size)
      assertNotNull(psiClass.implementsList!!.referenceElements.last().textRange)
    }
  }

  @Test
  fun testNavigation_high(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
    IdeaDecompilerSettings.getInstance().loadState(state)

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(getTestFile("Navigation.class"))
      doTestNavigation(11, 14, 14, 10)  // to "m2()"
      doTestNavigation(15, 21, 14, 17)  // to "int i"
      doTestNavigation(16, 28, 15, 13)  // to "int r"
    }
  }

  @Test
  fun testNavigation_medium(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.MEDIUM)
    IdeaDecompilerSettings.getInstance().loadState(state)

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(getTestFile("Navigation.class"))
      doTestNavigation(12, 14, 15, 10)  // to "m2()"
      doTestNavigation(16, 21, 15, 17)  // to "int i"
      doTestNavigation(17, 28, 16, 13)  // to "int r"
    }
  }

  @Test
  fun testNavigation_low(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.LOW)
    IdeaDecompilerSettings.getInstance().loadState(state)

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(getTestFile("Navigation.class"))
      doTestNavigation(12, 14, 15, 10)  // to "m2()"
      doTestNavigation(16, 21, 15, 17)  // to "int i"
      doTestNavigation(17, 28, 16, 13)  // to "int r"
    }
  }

  @Test
  fun testUnicode_high() {
    val state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH)
    IdeaDecompilerSettings.getInstance().loadState(state)

    val file = getTestFile("UnicodeTest.class")
    val decompiled = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { IdeaDecompiler().getText(file).toString() }
    assertTrue(decompiled.contains("你好"), decompiled)
    assertFalse(decompiled.contains("\\u4f60"), decompiled)
  }

  private fun doTestNavigation(line: Int, column: Int, expectedLine: Int, expectedColumn: Int) {
    val target = GotoDeclarationAction.findTargetElement(myFixture.project, myFixture.editor, offset(line, column)) as Navigatable
    target.navigate(true)
    val expected = offset(expectedLine, expectedColumn)
    assertEquals(expected, myFixture.caretOffset)
  }

  private fun offset(line: Int, column: Int): Int = myFixture.editor.document.getLineStartOffset(line - 1) + column - 1

  @Test
  fun testHighlighting(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.setReadEditorMarkupModel(true)
    withContext(Dispatchers.EDT) {
      IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(myFixture.project, Runnable {
        myFixture.openFileInEditor(getTestFile("Navigation.class"))
        myFixture.editor.caretModel.moveToOffset(offset(11, 14))  // m2(): usage, declaration
        assertEquals(2, highlightUnderCaret().size)
        myFixture.editor.caretModel.moveToOffset(offset(14, 10))  // m2(): usage, declaration
        assertEquals(2, highlightUnderCaret().size)
        myFixture.editor.caretModel.moveToOffset(offset(14, 17))  // int i: usage, declaration
        assertEquals(2, highlightUnderCaret().size)
        myFixture.editor.caretModel.moveToOffset(offset(15, 21))  // int i: usage, declaration
        assertEquals(2, highlightUnderCaret().size)
        myFixture.editor.caretModel.moveToOffset(offset(15, 13))  // int r: usage, declaration
        assertEquals(2, highlightUnderCaret().size)
        myFixture.editor.caretModel.moveToOffset(offset(16, 28))  // int r: usage, declaration
        assertEquals(2, highlightUnderCaret().size)
        myFixture.editor.caretModel.moveToOffset(offset(19, 24))  // throws: declaration, m4() call
        assertEquals(2, highlightUnderCaret().size)
      })
    }
  }

  @Test
  fun testNameHighlightingInsideCompiledFile(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.setReadEditorMarkupModel(true)
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(getTestFile("NamesHighlightingInsideCompiledFile.class"))
      IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(myFixture.project, Runnable {
        val infos = myFixture.doHighlighting()
        assertTrue(infos.all { info: HighlightInfo -> info.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY }, infos.toString())
        assertEquals(68, infos.size)
      })
    }
  }

  @Test
  fun testNameHighlightingInsideCompiledModuleFile(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.setReadEditorMarkupModel(true)
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(getTestFile("module-info.class"))
      IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(myFixture.project, Runnable {
        val infos = myFixture.doHighlighting()
          .filter { it.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY }
        assertEquals(5, infos.size)
        val texts = infos.map { it.text }.toSet()
        assertThat(texts).contains("module", "requires", "exports")
      })
    }
  }

  @Test
  fun testNameHighlightingInsideCompiledFileWithRecords(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.setReadEditorMarkupModel(true)
    val testFile = getTestFile("RecordHighlighting.class")
    testFile.parent.children; testFile.parent.refresh(false, true)  // inner classes
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(testFile)
      IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(myFixture.project, Runnable {
        val infos = myFixture.doHighlighting()
          .filter { it.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY }
        val texts = infos.map { it.text }.toSet()
        assertThat(texts).contains("sealed", "record", "permits")
      })
    }
  }

  private fun highlightUnderCaret(): List<HighlightInfo> {
    IdentifierHighlighterPassFactory.waitForIdentifierHighlighting(myFixture.editor)
    return myFixture.doHighlighting().filter { it.severity === HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY }
  }

  @Test
  fun testLineNumberMapping_high(): Unit = doTestLineMapping(DecompilerPreset.HIGH) { mapping ->
    assertEquals(11, mapping.bytecodeToSource(3)) // Assert that line 8 in decompiled class file maps to line 3 in Java source file
    assertEquals(21, mapping.bytecodeToSource(13))
    assertEquals(13, mapping.sourceToBytecode(21)) // Assert that line 13 in Java source file maps to line 18 in Java class file
    assertEquals(-1, mapping.bytecodeToSource(1000))
    assertEquals(-1, mapping.sourceToBytecode(1000))
  }

  @Test
  fun testLineNumberMapping_medium(): Unit = doTestLineMapping(DecompilerPreset.MEDIUM) { mapping ->
    assertEquals(12, mapping.bytecodeToSource(3)) // Assert that line 12 in decompiled class file maps to line 2 in Java source file
    assertEquals(3, mapping.sourceToBytecode(12))
    assertEquals(21, mapping.bytecodeToSource(12))
    assertEquals(12, mapping.sourceToBytecode(21))
    assertEquals(-1, mapping.bytecodeToSource(1000))
    assertEquals(-1, mapping.sourceToBytecode(1000))
  }

  @Test
  fun testLineNumberMapping_low(): Unit = doTestLineMapping(DecompilerPreset.LOW) { mapping ->
    assertEquals(12, mapping.bytecodeToSource(3)) // Assert that line 12 in decompiled class file maps to line 2 in Java source file
    assertEquals(3, mapping.sourceToBytecode(12))
    assertEquals(21, mapping.bytecodeToSource(12))
    assertEquals(12, mapping.sourceToBytecode(21))
    assertEquals(-1, mapping.bytecodeToSource(1000))
    assertEquals(-1, mapping.sourceToBytecode(1000))
  }

  private fun doTestLineMapping(preset: DecompilerPreset, assertion: (mapping: LineNumbersMapping) -> Unit) {
    Registry.get("decompiler.use.line.mapping").withValue(true) {
      val file = getTestFile("LineNumbers.class")
      try {
        val state = IdeaDecompilerSettings.State.fromPreset(preset)
        IdeaDecompilerSettings.getInstance().loadState(state)
        assertNull(file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY))

        BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { IdeaDecompiler().getText(file) }

        val mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)!!
        assertion(mapping)
      }
      finally {
        file.removeUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)
      }
    }
  }

  @Test
  fun testStructureView(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val file = getTestFile("StructureView.class")
    file.parent.children; file.parent.refresh(false, true)  // inner classes
    checkStructure(file, """
      -StructureView.class
       -StructureView
        -B
         B()
         build(int): StructureView
        StructureView()
        getData(): int
        setData(int): void
        data: int""")

    readAction { (PsiManager.getInstance(myFixture.project).findFile(file) as? PsiCompiledFile)?.decompiledPsiFile }

    checkStructure(file, """
      -StructureView.java
       -StructureView
        -B
         B()
         -build(int): StructureView
          -$1
           class initializer
        StructureView()
        getData(): int
        setData(int): void
        data: int""")
  }

  private suspend fun checkStructure(file: VirtualFile, s: String) {
    withContext(Dispatchers.EDT) {
      val editor = FileEditorManager.getInstance(myFixture.project).openFile(file, false)[0]
      val builder = StructureViewBuilder.getProvider().getStructureViewBuilder(JavaClassFileType.INSTANCE, file, myFixture.project)!!
      val svc = builder.createStructureView(editor, myFixture.project) as StructureViewComponent
      Disposer.register(myFixture.testRootDisposable, svc)
      svc.setActionActive(KindSorter.ID, true)
      svc.setActionActive(JavaAnonymousClassesNodeProvider.ID, true)
      PlatformTestUtil.expandAll(svc.tree)
      PlatformTestUtil.assertTreeEqual(svc.tree, s.trimIndent())
    }
  }

  private fun getTestFile(name: String): VirtualFile {
    val path = if (FileUtil.isAbsolute(name)) name else "${myFixture.testDataPath}/${name}"
    val fs = if (path.contains(URLUtil.JAR_SEPARATOR)) StandardFileSystems.jar() else StandardFileSystems.local()
    val file = fs.refreshAndFindFileByPath(path) ?: error("File not found: $path")
    if (file.isDirectory) file.refresh(false, true)
    return file
  }

  private class MyFileVisitor(private val psiManager: PsiManager) : VirtualFileVisitor<Any>() {
    private val negativeTests = setOf("TestUnsupportedConstantPoolEntry")

    override fun visitFile(file: VirtualFile): Boolean {
      if (file.isDirectory) {
        LOG.debug(file.path)
      }
      else if (file.fileType === JavaClassFileType.INSTANCE && !file.name.contains('$')) {
        val psiFile = psiManager.findFile(file)
        if (psiFile == null) {
          throw AssertionError("PSI file for ${file.name} not found")
        }
        if (psiFile.language != JavaLanguage.INSTANCE) {
          return true //do not test kotlin decompiler here
        }
        if (psiFile !is ClsFileImpl) {
          throw AssertionError("PSI file for ${file.name} should be an instance of ${ClsFileImpl::class.java.name}")
        }

        if (file.nameWithoutExtension in negativeTests) {
          assertEquals("corrupted_class_file", psiFile.packageName)
          return true
        }

        val decompiled = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation { psiFile.mirror.text }
        assertTrue(decompiled.startsWith(IDEA_DECOMPILER_BANNER) || file.name.endsWith("-info.class"), file.path)

        // check that no mapped line number is on an empty line
        val prefix = "// "
        decompiled.split("\n").dropLastWhile(String::isEmpty).toTypedArray().forEach { s ->
          val pos = s.indexOf(prefix)
          if (pos == 0 && prefix.length < s.length && Character.isDigit(s[prefix.length])) {
            fail("Incorrect line mapping in the file " + file.path + " line: " + s)
          }
        }
      }
      else if (file.fileType === ArchiveFileType.INSTANCE) {
        val jarRoot = JarFileSystem.getInstance().getRootByLocal(file)
        if (jarRoot != null) {
          VfsUtilCore.visitChildrenRecursively(jarRoot, this)
        }
      }

      return true
    }
  }
}