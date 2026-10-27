// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.testFramework

import com.intellij.codeInsight.highlighting.actions.HighlightUsagesAction
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Creates a [PolySymbolsFixture]. It is the JUnit 5 replacement of the JUnit 3 base [PolySymbolsTestCase].
 * Declare the result as an instance property, so that each test gets its own fixture.
 *
 * The parameters replace the overrides of the [PolySymbolsTestCase] properties with the same names.
 * [mode] replaces the mode of [HybridTestCase]. The generic [hybridFixture] factory describes the project of each mode.
 * To replace a hook method of [PolySymbolsTestCase], extend [PolySymbolsFixture] and create it with the generic [hybridFixture] factory.
 */
@TestOnly
@JvmOverloads
fun polySymbolsFixture(
  testDataRoot: String,
  testCasePath: String,
  defaultExtension: String,
  defaultDependencies: Map<String, String> = emptyMap(),
  dirModeByDefault: Boolean = false,
  mode: HybridTestMode = HybridTestMode.BasePlatform,
): TestFixture<PolySymbolsFixture> =
  hybridFixture({ mode }) {
    PolySymbolsFixture(it, testDataRoot, testCasePath, defaultExtension, defaultDependencies, dirModeByDefault)
  }

/**
 * The helpers of [PolySymbolsTestCase] for a JUnit 5 test.
 * Call a member from a background thread. A member takes the read actions and the EDT calls that it needs.
 * The members configure the project off the EDT and run the `test` lambda on the EDT, as the JUnit 3 base did.
 * The lambdas in the tests call the write actions, the editor actions, and the navigation helpers, which need the EDT.
 *
 * A sub-fixture overrides an `open` member to replace a hook of [PolySymbolsTestCase].
 * A hook runs off the EDT.
 */
@TestOnly
open class PolySymbolsFixture(
  context: HybridFixtureContext,
  val testDataRoot: String,
  val testCasePath: String,
  val defaultExtension: String,
  val defaultDependencies: Map<String, String>,
  val dirModeByDefault: Boolean = false,
) : HybridFixture(context, "$testDataRoot/$testCasePath") {

  private val myFixture: CodeInsightTestFixture get() = fixture

  open val defaultDirName: String
    get() = testName

  open fun getDefaultConfigureFileName(extension: String): String =
    "$testName.$extension"

  open fun getGoldFileName(forcedGoldFileName: String?, testFileExt: String): String =
    forcedGoldFileName ?: "${testName}_after.$testFileExt"

  open fun getCodeCompletionExpectedItemsFileNameInfix(prefix: String, suffix: String): String =
    ""

  open fun getCodeCompletionExpectedItemsLocation(dir: Boolean, dirName: String): String =
    getExpectedDataLocation(dir, dirName)

  /**
   * The test method name without the `test` prefix and with a lowercase first letter.
   */
  open val testName: String get() = getTestName(true)

  override fun setUp() {
    super.setUp()
    enableAstLoadingFilter()
  }

  /**
   * Replaces `UsefulTestCase.enableAstLoadingFilter()`. The change lasts until the end of the test.
   */
  fun enableAstLoadingFilter() {
    Registry.get("ast.loading.filter").setValue(true, testRootDisposable)
  }

  /**
   * Replaces `UsefulTestCase.disableAstLoadingFilter()`. The change lasts until the end of the test.
   */
  fun disableAstLoadingFilter() {
    Registry.get("ast.loading.filter").setValue(false, testRootDisposable)
  }

  /**
   * Replaces `UsefulTestCase.enableIdempotenceChecksOnEveryCache()`. The change lasts until the end of the test.
   */
  fun enableIdempotenceChecksOnEveryCache() {
    Registry.get("platform.random.idempotence.check.rate").setValue(1, testRootDisposable)
  }

  protected open fun beforeConfiguredTest(configuration: TestConfiguration) {

  }

  protected open fun afterConfiguredTest(configuration: TestConfiguration) {

  }

  protected open fun ensureIndexesReady() {
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject())
  }

  protected open fun waitForAsyncOperationsToCompleteAfterEdit() {

  }

  protected open val directoriesCompareFileFilter: VirtualFileFilter
    get() = { true }

  fun withTempCodeStyleSettings(test: CodeInsightTestFixture.(settings: CodeStyleSettings) -> Unit) {
    myFixture.testWithTempCodeStyleSettings { t: CodeStyleSettings ->
      myFixture.test(t)
    }
  }

  protected open fun adjustConfigurators(
    configurators: List<PolySymbolsTestConfigurator>,
  ): List<PolySymbolsTestConfigurator> =
    configurators

  /**
   * Configures the project, runs [test] on the EDT, and checks the result when [checkResult] is `true`.
   */
  fun doConfiguredTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFile: Boolean = true,
    configureFileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    checkResult: Boolean = false,
    goldFileName: String? = null,
    useProjectCodeStyle: Boolean = false,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)? = null,
    test: CodeInsightTestFixture.() -> Unit,
  ) {
    doConfiguredTestImpl(fileContents, dir, dirName, extension, configureFile, configureFileName, configurators, additionalFiles,
                         checkResult, goldFileName, useProjectCodeStyle, configureCodeStyleSettings) {
      runInEdtAndWait { test() }
    }
  }

  private fun doConfiguredTestImpl(
    fileContents: String?,
    dir: Boolean,
    dirName: String,
    extension: String,
    configureFile: Boolean,
    configureFileName: String,
    configurators: List<PolySymbolsTestConfigurator>,
    additionalFiles: List<String>,
    checkResult: Boolean,
    goldFileName: String?,
    useProjectCodeStyle: Boolean,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)?,
    test: CodeInsightTestFixture.() -> Unit,
  ) {
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
    if (mode == HybridTestMode.CodeInsightFixture) {
      // For some of LSP-based tests, we need a working directory
      project.basePath?.let { Path.of(it) }?.createDirectories()
    }
    myFixture.apply {
      if (dir) {
        if (checkResult) {
          copyDirectoryToProject("$dirName/before", ".")
        }
        else {
          copyDirectoryToProject(dirName, ".")
        }
      }
      else if (additionalFiles.isNotEmpty()) {
        configureByFiles(*additionalFiles.toTypedArray())
      }

      val adjustedConfigurators = adjustConfigurators(configurators)
      adjustedConfigurators.forEach {
        it.configure(myFixture)
      }
      // After copying the files, some files might have been indexed with incorrect PolyContext,
      // ensure we have all files scanned again and indexed correctly
      runInEdtAndWait {
        WriteAction.run<RuntimeException> {
          ProjectRootManagerEx.getInstanceEx(project)
            .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
        }
      }
      if (!isDumb(myFixture.getProject())) {
        ensureIndexesReady()
      }
      if (configureFile) {
        if (fileContents != null) {
          configureByText(configureFileName, fileContents)
        }
        else if (dir) {
          configureFromTempProjectFile(configureFileName)
            .also {
              it.virtualFile.putUserData(
                VfsTestUtil.TEST_DATA_FILE_PATH,
                "$testDataPath/$dirName/${if (checkResult) "before/" else ""}$configureFileName"
              )
            }
        }
        else {
          configureByFile(configureFileName)
        }
      }
      val testConfiguration = TestConfiguration(
        adjustedConfigurators
      )
      testWithTempCodeStyleSettings {
        if (!useProjectCodeStyle && configureCodeStyleSettings != null) {
          it.configureCodeStyleSettings()
        }
        else if (useProjectCodeStyle) {
          CodeStyleSettingsManager.getInstance(project).dropTemporarySettings()
        }
        beforeConfiguredTest(testConfiguration)
        ensureIndexesReady()
        try {
          test()
        }
        finally {
          afterConfiguredTest(testConfiguration)
        }
      }
      waitForAsyncOperationsToCompleteAfterEdit()
      if (checkResult) {
        WriteCommandAction.runWriteCommandAction(project) {
          PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
        }
        runInEdtAndWait {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
        if (dir) {
          val pathAfter = "$testDataPath/$dirName/after"
          val rootAfter = StandardFileSystems.local().findFileByPath(pathAfter)
                          ?: throw FileNotFoundException(pathAfter)
          val results = myFixture.tempDirFixture.findOrCreateDir(".")

          val skippedRootFiles = adjustedConfigurators.flatMapTo(HashSet()) { it.rootFilesToSkipInDirectoryComparison(myFixture, rootAfter) }
          val baseFilter = directoriesCompareFileFilter
          val filter = VirtualFileFilter { file ->
            baseFilter.accept(file)
            && !(file.name in skippedRootFiles && (file.parent == results || file.parent == rootAfter))
          }

          // Set test data file path, so that comparison works
          val root = tempDirFixture.findOrCreateDir(".")
          VfsUtil.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
            override fun visitFileEx(file: VirtualFile): Result =
              if (!filter.accept(file))
                SKIP_CHILDREN
              else {
                file.putUserData(
                  VfsTestUtil.TEST_DATA_FILE_PATH,
                  pathAfter + "/" + VfsUtil.getRelativePath(file, root)
                )
                CONTINUE
              }
          })

          runInEdtAndWait { PlatformTestUtil.assertDirectoriesEqual(rootAfter, results, filter) }
        }
        else {
          val ext = runReadActionBlocking { InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file) }
            .name.takeLastWhile { it != '.' }
          myFixture.checkResultByFile(getGoldFileName(goldFileName, ext))
        }
      }
    }
  }

  fun doCommentTest(commentStyle: CommentStyle, id: Int? = null, extension: String = defaultExtension) {
    val name = getTestName(true)
    myFixture.configureByFile("$name.$extension")
    try {
      myFixture.performEditorAction(
        if (commentStyle == CommentStyle.LINE) IdeActions.ACTION_COMMENT_LINE
        else IdeActions.ACTION_COMMENT_BLOCK
      )
      myFixture.checkResultByFile("${name}_after${id?.let { "_$it" } ?: ""}.$extension")
    }
    finally {
      myFixture.configureByFile("$name.$extension")
    }
  }

  fun doEditorTypingTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFile: Boolean = true,
    configureFileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    checkResult: Boolean = true,
    goldFileName: String? = null,
    useProjectCodeStyle: Boolean = false,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)? = null,
    before: CodeInsightTestFixture.() -> Unit = {},
    after: CodeInsightTestFixture.() -> Unit = {},
    test: EditorTypingTestFixture.() -> Unit,
  ) {
    // The typing runs off the EDT, so that the auto-popup tester can wait for the lookup.
    doConfiguredTestImpl(fileContents, dir, dirName, extension, configureFile, configureFileName, configurators, additionalFiles,
                         checkResult, goldFileName, useProjectCodeStyle, configureCodeStyleSettings) {
      runInEdtAndWait { before() }
      val tester = CompletionAutoPopupTester(myFixture)
      tester.runWithAutoPopupEnabled {
        object : EditorTypingTestFixture {
          private var checkLookupCount = 0

          override fun type(str: String) {
            for (i in str.indices) {
              myFixture.type(str[i])
              ApplicationManager.getApplication().invokeAndWait {
                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
                UIUtil.dispatchAllInvocationEvents()
              }
              WriteAction.runAndWait<Throwable> { PsiDocumentManager.getInstance(project).commitAllDocuments() }
              waitForAsyncOperationsToCompleteAfterEdit()
              tester.joinAutopopup()
              tester.joinCompletion()
              tester.joinCommit()
            }
          }

          override fun assertLookupShown() {
            assertNotNull(tester.lookup, "Lookup should be shown")
          }

          override fun assertLookupNotShown() {
            assertNull(tester.lookup, "Lookup should not be shown")
          }

          override fun completeBasic() {
            myFixture.completeBasic()
          }

          override fun moveToOffsetBySignature(signature: String) {
            invokeAndWaitIfNeeded {
              myFixture.moveToOffsetBySignature(signature)
            }
          }

          override fun checkLookupItems(
            renderPriority: Boolean,
            renderTypeText: Boolean,
            renderTailText: Boolean,
            renderProximity: Boolean,
            renderDisplayText: Boolean,
            renderDisplayEffects: Boolean,
            lookupItemFilter: (item: LookupElementInfo) -> Boolean,
          ) {
            assertLookupShown()
            tester.joinCompletion()

            val expectedFile = getCodeCompletionExpectedItemsLocation(dir, dirName) +
                               (if (dir) "/items" else "$testName.items") +
                               ".${++checkLookupCount}.txt"

            checkListByFile(
              actualList = renderLookupItems(
                renderPriority = renderPriority,
                renderTypeText = renderTypeText,
                renderTailText = renderTailText,
                renderProximity = renderProximity,
                renderDisplayText = renderDisplayText,
                renderDisplayEffects = renderDisplayEffects,
                lookupFilter = lookupItemFilter,
              ),
              expectedFile = expectedFile,
              containsCheck = false,
            )
          }

          override fun assertLookupContains(vararg items: String) {
            assertLookupShown()
            tester.joinCompletion()
            val lookupStrings = myFixture.lookupElementStrings!!
            val missing = items.filter { it !in lookupStrings }
            assertTrue(missing.isEmpty()) { "The lookup $lookupStrings does not contain $missing" }
          }

          override fun assertLookupDoesntContain(vararg items: String) {
            assertLookupShown()
            tester.joinCompletion()
            val lookupStrings = myFixture.lookupElementStrings!!
            val present = items.filter { it in lookupStrings }
            assertTrue(present.isEmpty()) { "The lookup $lookupStrings contains $present" }
          }

          override fun selectLookupItem(item: String) {
            assertLookupShown()
            tester.joinCompletion()
            invokeAndWaitIfNeeded {
              myFixture.lookup.let { lookup ->
                lookup.currentItem = lookup.items.firstOrNull { it.lookupString == item }
                                     ?: throw RuntimeException("Item '$item' not found")
              }
            }
          }

          override val editor: Editor
            get() = myFixture.editor

          override fun performEditorAction(actionId: String) {
            myFixture.performEditorAction(actionId)
          }

          override fun performHighlighting() {
            invokeAndWaitIfNeeded {
              try {
                myFixture.checkHighlighting()
              }
              catch (_: FileComparisonFailedError) {
                // ignore - it is important to just perform highlighting check
              }
            }
          }

          override fun checkHighlighting(
            expectedDataFile: String,
            checkWarnings: Boolean,
            checkInfos: Boolean,
            checkWeakWarnings: Boolean,
            ignoreExtraHighlighting: Boolean,
          ) {
            val filePath = testDataPath + (if (dir) "/$dirName/" else "/") + expectedDataFile
            val text = Path.of(filePath).readText()
            val document = DocumentImpl(text)
            val data = ExpectedHighlightingData(document, checkWarnings, checkWeakWarnings, checkInfos, ignoreExtraHighlighting)
            data.init()
            try {
              (myFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
            }
            catch (e: FileComparisonFailedError) {
              throw FileComparisonFailedError(e.message, e.expectedStringPresentation, e.actualStringPresentation,
                                              filePath, null)
            }
          }

        }.test()
      }
      runInEdtAndWait { after() }
    }
  }

  private fun getExpectedDataLocation(dir: Boolean, dirName: String): String =
    if (dir) dirName else ""

  fun doLookupTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    useProjectCodeStyle: Boolean = false,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)? = null,
    renderPriority: Boolean = true,
    renderTypeText: Boolean = true,
    renderTailText: Boolean = false,
    renderProximity: Boolean = false,
    renderPresentedText: Boolean = false,
    checkDocumentation: Boolean = false,
    containsCheck: Boolean = false,
    typeToFinishLookup: String? = null,
    goldFileName: String? = null,
    locations: List<String> = emptyList(),
    namedLocations: List<Pair<String, String>> = emptyList(),
    lookupItemFilter: (item: LookupElementInfo) -> Boolean = { true },
  ) {
    doConfiguredTest(
      fileContents = fileContents,
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFileName = configureFileName,
      additionalFiles = additionalFiles,
      configurators = configurators,
      useProjectCodeStyle = useProjectCodeStyle,
      configureCodeStyleSettings = configureCodeStyleSettings,
      checkResult = typeToFinishLookup != null,
      goldFileName = goldFileName,
    ) {
      assert(typeToFinishLookup == null || locations.isEmpty())
      caretPosSignature?.let { moveToOffsetBySignature(it) }
      checkLookupItems(
        renderPriority = renderPriority,
        renderTypeText = renderTypeText,
        renderTailText = renderTailText,
        renderProximity = renderProximity,
        renderDisplayText = renderPresentedText,
        checkDocumentation = checkDocumentation,
        containsCheck = containsCheck,
        locations = locations,
        namedLocations = namedLocations,
        expectedDataLocation = getExpectedDataLocation(dir, dirName),
        expectedItemsLocation = getCodeCompletionExpectedItemsLocation(dir, dirName),
        expectedItemsFileNameInfixProvider = ::getCodeCompletionExpectedItemsFileNameInfix,
        lookupItemFilter = lookupItemFilter,
      )
      if (typeToFinishLookup != null) {
        type(typeToFinishLookup.takeWhile { it != '\n' && it != '\t' && it != '\r' })
        finishLookup(typeToFinishLookup.firstOrNull { it == '\n' || it == '\t' || it == '\r' }
                     ?: Lookup.NORMAL_SELECT_CHAR)
      }
    }
  }

  fun doFormattingTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    goldFileName: String? = null,
    useProjectCodeStyle: Boolean = false,
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    configureCodeStyleSettings: CodeStyleSettings.() -> Unit = {},
  ) {
    doConfiguredTest(
      fileContents = fileContents,
      dir = dir,
      dirName = dirName,
      extension = extension,
      checkResult = true,
      goldFileName = goldFileName,
      configureFileName = configureFileName,
      configureCodeStyleSettings = configureCodeStyleSettings,
      configurators = configurators,
      additionalFiles = additionalFiles,
      useProjectCodeStyle = useProjectCodeStyle,
    ) {
      val codeStyleManager = CodeStyleManager.getInstance(project)
      WriteCommandAction.runWriteCommandAction(project) { codeStyleManager.reformat(file) }
    }
  }

  fun doFoldingTest(
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
  ) {
    doConfiguredTest(extension = extension, checkResult = false, configureFile = false, configurators = configurators) {
      testFolding("$testDataPath/$configureFileName")
    }
  }

  fun doHighlightingTest(
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    inspections: Collection<Class<out LocalInspectionTool>> = emptyList(),
    checkSymbolNames: Boolean = false,
    checkWarnings: Boolean = true,
    checkWeakWarnings: Boolean = true,
    checkInformation: Boolean = checkSymbolNames,
    checkInjections: Boolean = false,
    setup: CodeInsightTestFixture.() -> Unit = {},
  ) {
    doConfiguredTest(
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFileName = configureFileName,
      configurators = configurators,
      additionalFiles = additionalFiles,
    ) {
      enableInspections(inspections)
      setup()
      if (checkSymbolNames || checkInjections) {
        checkHighlightingEx(checkWarnings, checkInformation, checkWeakWarnings, checkSymbolNames, checkInjections)
      }
      else {
        runHighlightingCheck(checkWarnings, checkInformation, checkWeakWarnings)
      }
    }
  }

  /**
   * Runs the highlighting check of [doHighlightingTest].
   * Override this to wait for asynchronous highlighting sources, for example the LSP diagnostics.
   */
  protected open fun CodeInsightTestFixture.runHighlightingCheck(
    checkWarnings: Boolean,
    checkInformation: Boolean,
    checkWeakWarnings: Boolean,
  ) {
    checkHighlighting(checkWarnings, checkInformation, checkWeakWarnings)
  }

  /**
   * Collects the highlighting and compares it with [data].
   * Override this to wait for asynchronous highlighting sources, for example the LSP diagnostics.
   */
  protected open fun CodeInsightTestFixture.collectAndCheckExpectedHighlighting(data: ExpectedHighlightingData) {
    (this as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
  }

  fun CodeInsightTestFixture.checkHighlightingEx(
    checkWarnings: Boolean = true,
    checkInfos: Boolean = false,
    checkWeakWarnings: Boolean = true,
    checkSymbolNames: Boolean = false,
    checkInjections: Boolean = false,
  ) {
    val data = ExpectedHighlightingData(getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos)
    if (checkSymbolNames) {
      data.checkSymbolNames()
    }
    data.init()
    if (checkInjections) {
      runInEdtAndWait { PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments() }
      val injectedLanguageManager = InjectedLanguageManager.getInstance(myFixture.getProject())
      // We need to ensure that injections are cached before we run PolySymbolsInspectionsPass
      SyntaxTraverser.psiTraverser(myFixture.getFile())
        .forEach { if (it is PsiLanguageInjectionHost) injectedLanguageManager.getInjectedPsiFiles(it) }
    }
    collectAndCheckExpectedHighlighting(data)
  }

  fun doParameterInfoTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    goldFileName: String = if (dir) "$defaultDirName/param-info.html" else "$testName.param-info.html",
  ) {
    doConfiguredTest(
      fileContents = fileContents,
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFileName = configureFileName,
      configurators = configurators,
      additionalFiles = additionalFiles
    ) {
      caretPosSignature?.let { moveToOffsetBySignature(it) }
      val info = getParameterInfoAtCaret()
      assertNotNull(info, "Parameter info was not provided")
      val testFilePath = Path.of("$testDataPath/$goldFileName")
      if (!testFilePath.exists()) {
        testFilePath.createFile()
        thisLogger().warn("File $testFilePath has been created.")
      }
      checkTextByFile(info!!, goldFileName)
    }
  }

  fun doGotoDeclarationTest(
    declarationSignature: String,
    fromSignature: String? = null,
    expectedFileName: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
  ) {
    doConfiguredTest(
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFileName = configureFileName,
      configurators = configurators,
      additionalFiles = additionalFiles,
    ) {
      fromSignature?.let { moveToOffsetBySignature(it) }
      checkGotoDeclaration(null, declarationSignature, expectedFileName = expectedFileName)
    }
  }

  fun doJumpToSourceTest(
    targetSignature: String,
    fromSignature: String? = null,
    expectedFileName: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
  ) {
    doConfiguredTest(
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFileName = configureFileName,
      configurators = configurators,
      additionalFiles = additionalFiles
    ) {
      fromSignature?.let { moveToOffsetBySignature(it) }
      checkJumpToSource(fromSignature, targetSignature, expectedFileName = expectedFileName)
    }
  }

  fun doFindUsagesTest(
    scope: SearchScope? = null,
    expectedFileName: String = "${defaultDirName}/usages.txt",
    gotoDeclarationOrUsageOutcome: GotoDeclarationOrUsageHandler2.GTDUOutcome? = GotoDeclarationOrUsageHandler2.GTDUOutcome.SU,
    dir: Boolean = true,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
  ) {
    doConfiguredTest(
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFileName = configureFileName,
      configurators = configurators,
      additionalFiles = additionalFiles
    ) {
      caretPosSignature?.let { moveToOffsetBySignature(it) }
      checkGTDUOutcome(gotoDeclarationOrUsageOutcome)
      checkListByFile(usagesAtCaret(scope = scope, usagesTestHelper = usagesTestHelper), expectedFileName, false)
    }
  }

  fun doFileUsagesTest(
    scope: SearchScope? = null,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    fileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    expectedFileName: String = "${defaultDirName}/usages.txt",
  ) {
    doConfiguredTest(dir = true, dirName = dirName, extension = extension, configureFileName = fileName, configurators = configurators) {
      checkListByFile(fileUsages(scope = scope, usagesTestHelper = usagesTestHelper).sorted(), expectedFileName, false)
    }
  }

  fun doUsageHighlightingTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
  ) {
    doConfiguredTest(
      fileContents = fileContents,
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFileName = configureFileName,
      configurators = configurators,
      additionalFiles = additionalFiles
    ) {
      val file = InjectedLanguageManager.getInstance(project).getTopLevelFile(file)
      val document = getDocument(file)
      val editor = (editor as? EditorWindow)?.delegate ?: editor
      WriteAction.run<Throwable> {
        var indexOf: Int
        WriteCommandAction.runWriteCommandAction(project) {
          while (document.charsSequence.indexOf("<usage>").also { indexOf = it } >= 0) {
            document.replaceString(indexOf, indexOf + "<usage>".length, "")
          }
          while (document.charsSequence.indexOf("</usage>").also { indexOf = it } >= 0) {
            document.replaceString(indexOf, indexOf + "</usage>".length, "")
          }
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
      caretPosSignature?.let { moveToOffsetBySignature(it) }
      testAction(HighlightUsagesAction())
      val highlighters = editor.getMarkupModel().getAllHighlighters()
      val usages = TreeMap<Int, Int>(Comparator.reverseOrder())
      highlighters.forEach {
        val highlighterEx = it as RangeHighlighterEx
        usages[highlighterEx.getAffectedAreaStartOffset()] = highlighterEx.getAffectedAreaEndOffset()
      }
      WriteAction.run<Throwable> {
        WriteCommandAction.runWriteCommandAction(project) {
          usages.forEach {
            document.replaceString(it.value, it.value, "</usage>")
            document.replaceString(it.key, it.key, "<usage>")
          }
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
      checkResultByFile(getDefaultConfigureFileName(extension))
    }
  }

  fun doSymbolRenameTest(
    newName: String,
    signature: String? = null,
    searchCommentsAndText: Boolean = false,
    testDialog: TestDialog? = null,
    dir: Boolean = true,
    dirName: String = defaultDirName,
    extension: String = defaultExtension,
    configureFileName: String = getDefaultConfigureFileName(extension),
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    useProjectCodeStyle: Boolean = false,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)? = null,
  ) {
    doSymbolRenameTest(
      configureFileName,
      newName,
      signature = signature,
      searchCommentsAndText = searchCommentsAndText,
      testDialog = testDialog,
      dir = dir,
      dirName = dirName,
      configurators = configurators,
      additionalFiles = additionalFiles,
      useProjectCodeStyle = useProjectCodeStyle,
      configureCodeStyleSettings = configureCodeStyleSettings,
    )
  }

  fun doSymbolRenameTest(
    mainFile: String,
    newName: String,
    signature: String? = null,
    searchCommentsAndText: Boolean = false,
    testDialog: TestDialog? = null,
    dir: Boolean = true,
    dirName: String = defaultDirName,
    goldFileName: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = emptyList(),
    additionalFiles: List<String> = emptyList(),
    useProjectCodeStyle: Boolean = false,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)? = null,
  ) {
    setTestDialog(testDialog)
    doConfiguredTest(
      dir = dir,
      dirName = dirName,
      checkResult = true,
      goldFileName = goldFileName,
      configureFileName = mainFile,
      configurators = configurators,
      additionalFiles = additionalFiles,
      useProjectCodeStyle = useProjectCodeStyle,
      configureCodeStyleSettings = configureCodeStyleSettings,
    ) {
      signature?.let { moveToOffsetBySignature(it) }
      renameSymbolAtCaret(newName, searchCommentsAndText)
    }
  }

  fun doFileRenameTest(
    newName: String,
    mainFile: String,
    searchCommentsAndText: Boolean = true,
    testDialog: TestDialog? = null,
    dirName: String = defaultDirName,
  ) {
    setTestDialog(testDialog)
    doConfiguredTest(dir = true, dirName = dirName, checkResult = true, configureFileName = mainFile) {
      renameElement(file, newName, searchCommentsAndText, searchCommentsAndText)
    }
  }

  open val usagesTestHelper: UsagesTestHelper
    get() = UsagesTestHelper.Default

  fun usagesAtCaret(scope: SearchScope? = null): List<String> =
    myFixture.usagesAtCaret(scope, usagesTestHelper)

  fun checkUsages(
    signature: String,
    goldFileName: String,
    strict: Boolean = true,
    scope: SearchScope? = null,
  ) {
    myFixture.checkUsages(signature, goldFileName, usagesTestHelper, strict, scope)
  }

  @Suppress("unused")
  fun checkFileUsages(
    goldFileName: String,
    strict: Boolean = true,
    scope: SearchScope? = null,
  ) {
    myFixture.checkFileUsages(goldFileName, usagesTestHelper, strict, scope)
  }

  enum class CommentStyle {
    LINE,
    BLOCK
  }

  interface EditorTypingTestFixture {
    fun type(str: String)

    fun assertLookupShown()

    fun assertLookupNotShown()

    fun completeBasic()

    fun moveToOffsetBySignature(signature: String)

    fun checkLookupItems(
      renderPriority: Boolean = true,
      renderTypeText: Boolean = true,
      renderTailText: Boolean = false,
      renderProximity: Boolean = false,
      renderDisplayText: Boolean = false,
      renderDisplayEffects: Boolean = renderPriority,
      lookupItemFilter: (item: LookupElementInfo) -> Boolean = { true },
    )

    fun assertLookupContains(vararg items: String)

    fun assertLookupDoesntContain(vararg items: String)

    fun selectLookupItem(item: String)

    fun performEditorAction(actionId: String)

    /**
     * Perform highlighting of the whole file,
     * ignoring any FileComparisonFailedError.
     */
    fun performHighlighting()

    fun checkHighlighting(
      expectedDataFile: String,
      checkWarnings: Boolean = true,
      checkInfos: Boolean = false,
      checkWeakWarnings: Boolean = true,
      ignoreExtraHighlighting: Boolean = false,
    )

    val editor: Editor
  }

  data class TestConfiguration(
    val configurators: List<PolySymbolsTestConfigurator>,
  )

  private fun setTestDialog(testDialog: TestDialog? = null) {
    testDialog?.let { TestDialogManager.setTestDialog(testDialog) }
    Disposer.register(testRootDisposable) {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    }
  }

}
