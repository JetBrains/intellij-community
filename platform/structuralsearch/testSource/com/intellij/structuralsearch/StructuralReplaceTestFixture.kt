// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions
import com.intellij.structuralsearch.plugin.replace.impl.Replacer
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertNull
import java.io.File
import java.io.IOException

/**
 * Creates a [StructuralReplaceTestFixture] on the project of [projectFixture].
 * Declare the result as an instance property, so that each test gets new [StructuralReplaceTestFixture.options].
 * Declare [projectFixture] in the companion as an opened project.
 * [loadFile][StructuralReplaceTestFixture.loadFile] reads its files from [testDataPath].
 * A [replace][StructuralReplaceTestFixture.replace] call with `createPhysicalFile` puts its source file in [sourceRootFixture].
 * Each test gets temporary code style settings, as in a light JUnit 3 test, so a test can change them.
 */
@TestOnly
@JvmOverloads
fun structuralReplaceFixture(
  projectFixture: TestFixture<Project>,
  testDataPath: String? = null,
  sourceRootFixture: TestFixture<PsiDirectory>? = null,
): TestFixture<StructuralReplaceTestFixture> = testFixture("structuralReplaceFixture") {
  val project = projectFixture.init()
  val fixture = StructuralReplaceTestFixture(project, testDataPath, sourceRootFixture?.init())
  CodeStyle.setTemporarySettings(project, CodeStyle.createTestSettings())
  initialized(fixture) {
    CodeStyle.dropTemporarySettings(project)
    fixture.deleteSourceFile()
  }
}

/**
 * The replace options and the replace helpers of a structural replace test.
 * The helpers compile the pattern in a read action, and they run the replacement in a command on the EDT.
 * So a test can call them off the EDT.
 */
@TestOnly
class StructuralReplaceTestFixture internal constructor(
  val project: Project,
  private val testDataPath: String?,
  private val sourceRoot: PsiDirectory?,
) {
  /**
   * The options of [replace] and [testReplace]. Each test gets new options.
   */
  var options: ReplaceOptions = ReplaceOptions()

  private var sourceFile: VirtualFile? = null

  /**
   * Reads [fileName] from the subdirectory of the test data path that has the name of the file extension.
   */
  @Throws(IOException::class)
  fun loadFile(fileName: String): String {
    val path = checkNotNull(testDataPath) { "Pass the test data path to structuralReplaceFixture" }
    return FileUtilRt.loadFile(File(path + FileUtilRt.getExtension(fileName) + "/" + fileName), CharsetToolkit.UTF8, true)
  }

  fun replace(`in`: String?, what: String, by: String): String = replace(`in`, what, by, false)

  fun replace(`in`: String?, what: String, by: String, sourceIsFile: Boolean): String = replace(`in`, what, by, sourceIsFile, false)

  /**
   * Replaces [what] with [by] in [in], with the file type of [options].
   * When [in] is `null`, the scope of the match options gives the source.
   * With [createPhysicalFile], the source is a file in the source root of [structuralReplaceFixture].
   * The file is deleted at the end of the test.
   */
  fun replace(`in`: String?, what: String, by: String, sourceIsFile: Boolean, createPhysicalFile: Boolean): String {
    require(`in` != null || !(sourceIsFile || createPhysicalFile)) { "can't create file when 'in' argument is null" }
    val matchOptions = options.matchOptions
    if (createPhysicalFile) {
      matchOptions.scope = LocalSearchScope(createSourceFile("Source." + matchOptions.fileType!!.defaultExtension, `in`!!))
    }
    matchOptions.fillSearchCriteria(what)
    val message = timeoutRunBlocking {
      readActionBlocking {
        val compiledPattern = PatternCompiler.compilePattern(project, matchOptions, true, false)
        StructuralSearchTestFixture.checkApplicableConstraints(matchOptions, compiledPattern)
      }
    }
    assertNull(message, message)
    return inCommandOnEdt { Replacer.testReplace(`in`, what, by, options, project, sourceIsFile) }
  }

  /**
   * Calls [Replacer.testReplace] with [options] on the EDT.
   */
  fun testReplace(
    `in`: String,
    what: String,
    by: String,
    sourceIsFile: Boolean,
    createPhysicalFile: Boolean,
    sourceFileType: LanguageFileType,
    sourceDialect: Language,
  ): String = inCommandOnEdt {
    Replacer.testReplace(`in`, what, by, options, project, sourceIsFile, createPhysicalFile, sourceFileType, sourceDialect)
  }

  /**
   * Runs [action] in a command on the EDT, because [Replacer] writes the replacement in a write action on the EDT.
   * A physical source file needs the command, as in a light JUnit 3 test.
   * The command does not swallow an exception of [action], so a test can check it.
   */
  private fun <T> inCommandOnEdt(action: () -> T): T = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      var result: Result<T>? = null
      CommandProcessor.getInstance().executeCommand(project, { result = runCatching(action) }, null, null)
      checkNotNull(result).getOrThrow()
    }
  }

  private fun createSourceFile(name: String, text: String): PsiFile {
    val root = checkNotNull(sourceRoot) { "Pass the source root fixture to structuralReplaceFixture" }
    return timeoutRunBlocking {
      val file = edtWriteAction {
        sourceFile?.delete(this@StructuralReplaceTestFixture)
        root.virtualFile.createChildData(this@StructuralReplaceTestFixture, name).also { VfsUtil.saveText(it, text) }
      }
      sourceFile = file
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
      readAction { checkNotNull(PsiManager.getInstance(project).findFile(file)) }
    }
  }

  internal suspend fun deleteSourceFile() {
    val file = sourceFile ?: return
    sourceFile = null
    edtWriteAction {
      if (file.isValid) {
        file.delete(this@StructuralReplaceTestFixture)
      }
    }
  }
}
