package com.intellij.polySymbols.testFramework

import com.intellij.codeInsight.TargetElementUtil
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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.ui.UIUtil
import org.editorconfig.Utils
import org.editorconfig.configmanagement.extended.EditorConfigCodeStyleSettingsModifier
import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

abstract class PolySymbolsTestCase(mode: HybridTestMode = HybridTestMode.BasePlatform) : HybridTestCase(mode) {

  protected abstract val testDataRoot: String

  protected abstract val testCasePath: String

  protected abstract val defaultExtension: String

  protected abstract val defaultDependencies: Map<String, String>

  protected open val defaultConfigurators: List<PolySymbolsTestConfigurator> = emptyList()

  protected open val dirModeByDefault: Boolean = false

  val testName: String get() = getTestName(true)

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    enableAstLoadingFilter()
  }

  final override fun getTestDataPath(): String = "$testDataRoot/${testCasePath}"

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

  protected open fun getExpectedItemsLocation(dir: Boolean): String =
    getExpectedDataLocation(dir)

  protected fun withTempCodeStyleSettings(test: CodeInsightTestFixture.(settings: CodeStyleSettings) -> Unit) {
    myFixture.testWithTempCodeStyleSettings { t: CodeStyleSettings ->
      myFixture.test(t)
    }
  }

  fun doConfiguredTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFile: Boolean = true,
    configureFileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
    additionalFiles: List<String> = emptyList(),
    checkResult: Boolean = false,
    goldFileName: String? = null,
    editorConfigEnabled: Boolean = false,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)? = null,
    test: CodeInsightTestFixture.() -> Unit,
  ) {
    if (editorConfigEnabled) {
      EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(true)
      Utils.isEnabledInTests = true
    }
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
    try {
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

        configurators.forEach {
          it.configure(myFixture)
        }
        // After copying the files, some files might have been indexed with incorrect PolyContext,
        // ensure we have all files scanned again and indexed correctly
        WriteAction.run<RuntimeException> {
          ProjectRootManagerEx.getInstanceEx(project)
            .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
        }
        ensureIndexesReady()
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
          configurators
        )
        if (!editorConfigEnabled && configureCodeStyleSettings != null) {
          testWithTempCodeStyleSettings {
            it.configureCodeStyleSettings()
            beforeConfiguredTest(testConfiguration)
            ensureIndexesReady()
            try {
              test()
            }
            finally {
              afterConfiguredTest(testConfiguration)
            }
          }
        }
        else {
          if (editorConfigEnabled) {
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
          WriteCommandAction.runWriteCommandAction(getProject()) {
            PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting()
          }
          FileDocumentManager.getInstance().saveAllDocuments()
          if (dir) {
            val pathAfter = "$testDataPath/$dirName/after"
            val rootAfter = LocalFileSystem.getInstance().findFileByPath(pathAfter)
                            ?: throw FileNotFoundException(pathAfter)
            val results = myFixture.tempDirFixture.findOrCreateDir(".")

            // Trigger any advanced configurators
            configurators.forEach { it.beforeDirectoryComparison(myFixture, results, rootAfter) }

            // Set test data file path, so that comparison works
            val root = tempDirFixture.findOrCreateDir(".")
            val filter = directoriesCompareFileFilter
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

            PlatformTestUtil.assertDirectoriesEqual(rootAfter, results, filter)
          }
          else {
            val ext = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)
              .name.takeLastWhile { it != '.' }
            myFixture.checkResultByFile(goldFileName ?: "${testName}_after.$ext")
          }
        }
      }
    }
    finally {
      if (editorConfigEnabled) {
        EditorConfigCodeStyleSettingsModifier.Handler.setEnabledInTests(false)
        Utils.isEnabledInTests = false
      }
    }
  }

  protected fun doCommentTest(commentStyle: CommentStyle, id: Int? = null, extension: String = defaultExtension) {
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

  protected fun doEditorTypingTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFile: Boolean = true,
    configureFileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
    additionalFiles: List<String> = emptyList(),
    checkResult: Boolean = true,
    goldFileName: String? = null,
    editorConfigEnabled: Boolean = false,
    configureCodeStyleSettings: (CodeStyleSettings.() -> Unit)? = null,
    before: CodeInsightTestFixture.() -> Unit = {},
    after: CodeInsightTestFixture.() -> Unit = {},
    test: EditorTypingTestFixture.() -> Unit,
  ) {
    doConfiguredTest(
      fileContents = fileContents,
      dir = dir,
      dirName = dirName,
      extension = extension,
      configureFile = configureFile,
      configureFileName = configureFileName,
      checkResult = checkResult,
      goldFileName = goldFileName,
      configurators = configurators,
      additionalFiles = additionalFiles,
      editorConfigEnabled = editorConfigEnabled,
      configureCodeStyleSettings = configureCodeStyleSettings,
    ) {
      before()
      val tester = CompletionAutoPopupTester(myFixture)
      var finished = false
      var exception: Throwable? = null
      ApplicationManager.getApplication().executeOnPooledThread {
        try {
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
                assertNotNull("Lookup should be shown", tester.lookup)
              }

              override fun assertLookupNotShown() {
                assertNull("Lookup should not be shown", tester.lookup)
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

                val expectedFile = getExpectedItemsLocation(dir) +
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
                assertContainsElements(myFixture.lookupElementStrings!!, *items)
              }

              override fun assertLookupDoesntContain(vararg items: String) {
                assertLookupShown()
                tester.joinCompletion()
                assertDoesntContain(myFixture.lookupElementStrings!!, *items)
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

            }.test()
          }
        }
        catch (@Suppress("IncorrectCancellationExceptionHandling") t: Throwable) {
          exception = t
        }
        finally {
          finished = true
        }
      }
      val start = System.currentTimeMillis()
      while (!finished) {
        if (System.currentTimeMillis() - start > 2.minutes.inWholeMilliseconds) {
          fail("Too long completion auto-popup test.")
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        Thread.sleep(2)
      }
      exception?.let { throw it }
      after()
    }
  }

  private fun getExpectedDataLocation(dir: Boolean): String =
    if (dir) testName else ""

  protected fun doLookupTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
    additionalFiles: List<String> = emptyList(),
    editorConfigEnabled: Boolean = false,
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
      editorConfigEnabled = editorConfigEnabled,
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
        expectedDataLocation = getExpectedDataLocation(dir),
        expectedItemsLocation = getExpectedItemsLocation(dir),
        lookupItemFilter = lookupItemFilter,
      )
      if (typeToFinishLookup != null) {
        type(typeToFinishLookup.takeWhile { it != '\n' && it != '\t' && it != '\r' })
        finishLookup(typeToFinishLookup.firstOrNull { it == '\n' || it == '\t' || it == '\r' }
                     ?: Lookup.NORMAL_SELECT_CHAR)
      }
    }
  }

  protected fun doFormattingTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    goldFileName: String? = null,
    editorConfigEnabled: Boolean = false,
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
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
      editorConfigEnabled = editorConfigEnabled,
    ) {
      val codeStyleManager = CodeStyleManager.getInstance(project)
      WriteCommandAction.runWriteCommandAction(project) { codeStyleManager.reformat(file) }
    }
  }

  protected fun doFoldingTest(
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
  ) {
    doConfiguredTest(extension = extension, checkResult = false, configureFile = false, configurators = configurators) {
      testFolding("$testDataPath/$configureFileName")
    }
  }

  protected fun doHighlightingTest(
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
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
        this.checkHighlighting(checkWarnings, checkInformation, checkWeakWarnings)
      }
    }
  }

  protected fun CodeInsightTestFixture.checkHighlightingEx(
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
    (this as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
  }

  protected fun doParameterInfoTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
    additionalFiles: List<String> = emptyList(),
    goldFileName: String = if (dir) "$testName/param-info.html" else "$testName.param-info.html",
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
      assertNotNull("Parameter info was not provided", info)
      val testFilePath = Path.of("$testDataPath/$goldFileName")
      if (!testFilePath.exists()) {
        testFilePath.createFile()
        thisLogger().warn("File $testFilePath has been created.")
      }
      checkTextByFile(info!!, goldFileName)
    }
  }

  protected fun doGotoDeclarationTest(
    declarationSignature: String,
    fromSignature: String? = null,
    expectedFileName: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
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

  protected fun doJumpToSourceTest(
    targetSignature: String,
    fromSignature: String? = null,
    expectedFileName: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
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

  protected fun doFindUsagesTest(
    scope: SearchScope? = null,
    expectedFileName: String = "${testName}/usages.txt",
    gotoDeclarationOrUsageOutcome: GotoDeclarationOrUsageHandler2.GTDUOutcome? = GotoDeclarationOrUsageHandler2.GTDUOutcome.SU,
    dir: Boolean = true,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
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

  protected fun doFileUsagesTest(
    scope: SearchScope? = null,
    dirName: String = testName,
    extension: String = defaultExtension,
    fileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
    expectedFileName: String = "${testName}/usages.txt",
  ) {
    doConfiguredTest(dir = true, dirName = dirName, extension = extension, configureFileName = fileName, configurators = configurators) {
      checkListByFile(fileUsages(scope = scope, usagesTestHelper = usagesTestHelper).sorted(), expectedFileName, false)
    }
  }

  protected fun doUsageHighlightingTest(
    fileContents: String? = null,
    dir: Boolean = dirModeByDefault,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    caretPosSignature: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
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
      checkResultByFile("$testName.$extension")
    }
  }

  protected fun doSymbolRenameTest(
    newName: String,
    signature: String? = null,
    searchCommentsAndText: Boolean = false,
    testDialog: TestDialog? = null,
    dir: Boolean = true,
    dirName: String = testName,
    extension: String = defaultExtension,
    configureFileName: String = "$testName.$extension",
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
    additionalFiles: List<String> = emptyList(),
    editorConfigEnabled: Boolean = false,
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
      editorConfigEnabled = editorConfigEnabled,
      configureCodeStyleSettings = configureCodeStyleSettings,
    )
  }

  protected fun doSymbolRenameTest(
    mainFile: String,
    newName: String,
    signature: String? = null,
    searchCommentsAndText: Boolean = false,
    testDialog: TestDialog? = null,
    dir: Boolean = true,
    dirName: String = testName,
    goldFileName: String? = null,
    configurators: List<PolySymbolsTestConfigurator> = defaultConfigurators,
    additionalFiles: List<String> = emptyList(),
    editorConfigEnabled: Boolean = false,
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
      editorConfigEnabled = editorConfigEnabled,
      configureCodeStyleSettings = configureCodeStyleSettings,
    ) {
      signature?.let { moveToOffsetBySignature(it) }
      if (canRenamePolySymbolAtCaret()) {
        renamePolySymbol(newName)
      }
      else {
        var targetElement = TargetElementUtil.findTargetElement(
          editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED)
        if (targetElement == null)
          throw AssertionError("No Symbol or PSI Element to rename at caret position.")
        targetElement = RenamePsiElementProcessor.forElement(targetElement)
          .substituteElementToRename(targetElement, editor)
        val renameProcessor = RenameProcessor(project, targetElement!!, newName, searchCommentsAndText, searchCommentsAndText)
        renameProcessor.run()
      }
    }
  }

  protected fun doFileRenameTest(
    newName: String,
    mainFile: String,
    searchCommentsAndText: Boolean = true,
    testDialog: TestDialog? = null,
    dirName: String = testName,
  ) {
    setTestDialog(testDialog)
    doConfiguredTest(dir = true, dirName = dirName, checkResult = true, configureFileName = mainFile) {
      renameElement(file, newName, searchCommentsAndText, searchCommentsAndText)
    }
  }

  protected open val usagesTestHelper: UsagesTestHelper
    get() = UsagesTestHelper.Default

  protected fun usagesAtCaret(scope: SearchScope? = null): List<String> =
    myFixture.usagesAtCaret(scope, usagesTestHelper)

  protected fun checkUsages(
    signature: String,
    goldFileName: String,
    strict: Boolean = true,
    scope: SearchScope? = null,
  ) {
    myFixture.checkUsages(signature, goldFileName, usagesTestHelper, strict, scope)
  }

  @Suppress("unused")
  protected fun checkFileUsages(
    goldFileName: String,
    strict: Boolean = true,
    scope: SearchScope? = null,
  ) {
    myFixture.checkFileUsages(goldFileName, usagesTestHelper, strict, scope)
  }

  protected enum class CommentStyle {
    LINE,
    BLOCK
  }

  protected interface EditorTypingTestFixture {
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
  }

  protected data class TestConfiguration(
    val configurators: List<PolySymbolsTestConfigurator>,
  )

  private fun setTestDialog(testDialog: TestDialog? = null) {
    testDialog?.let { TestDialogManager.setTestDialog(testDialog) }
    Disposer.register(testRootDisposable) {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    }
  }

}