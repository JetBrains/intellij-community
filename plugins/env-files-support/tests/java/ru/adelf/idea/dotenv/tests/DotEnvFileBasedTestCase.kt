package ru.adelf.idea.dotenv.tests

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.LexerTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import ru.adelf.idea.dotenv.extension.symbols.DotEnvKeySymbol
import ru.adelf.idea.dotenv.grammars.DotEnvLexerAdapter

abstract class DotEnvFileBasedTestCase : BasePlatformTestCase() {

    @Override
    override fun setUp() {
        super.setUp()
        val filename = filenamePrefixForCurrentTest("env")
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(filename))
    }

    private fun filenamePrefixForCurrentTest(extension: String): String {
        val fixtureName = getTestName(true)
            .replace(Regex("^test"), "")
            .replaceFirstChar { it.lowercaseChar() }
        return "$fixtureName.$extension"
    }

    fun doPsiDumpTest() {
        val referenceFile = "${testDataPath}/${filenamePrefixForCurrentTest("txt")}"
        val actual = DebugUtil.psiToString(myFixture.file, true)
        assertSameLinesWithFile(referenceFile, actual)
    }

    fun doLexerTest() {
        val referenceFile = "${testDataPath}/${filenamePrefixForCurrentTest("txt")}"
        val text = myFixture.file.text
        val actual = LexerTestCase.printTokens(text, 0, DotEnvLexerAdapter())
        assertSameLinesWithFile(referenceFile, actual)
    }

    fun doInspectionTest(inspection: LocalInspectionTool) {
        myFixture.enableInspections(inspection)
        myFixture.testHighlighting(true, true, true)
    }

    fun doQuickFixTest(inspection: LocalInspectionTool) {
        myFixture.enableInspections(inspection)
        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Put value inside double quotes")
        myFixture.launchAction(intention)
        val referenceFile = filenamePrefixForCurrentTest("txt")
        myFixture.copyFileToProject(referenceFile)
        myFixture.checkResultByFile(referenceFile)
    }

    fun doUsageTest() {
        var result: String? = null
        runInEdtAndWait {
            result = myFixture.testFindUsagesUsingAction()
                .sortedBy { it.navigationOffset }
                .joinToString("\n")
        }
        val referenceFile = "${testDataPath}/${filenamePrefixForCurrentTest("txt")}"
        assertSameLinesWithFile(referenceFile, result!!)
    }

    fun doSingleCompletionTest() {
        val settings = CodeInsightSettings.getInstance()
        val command = Runnable {
            val previousCompletionState: Boolean = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
            (LookupManager.getActiveLookup(myFixture.editor) as? LookupImpl)?.finishLookup('\n')
            settings.AUTOCOMPLETE_ON_CODE_COMPLETION = previousCompletionState
        }
        CommandProcessor.getInstance().executeCommand(project, command, null, null, myFixture.editor.document)
    }

    fun doRenameTest(newName: String, vararg fileExtensions: String = arrayOf("env")) {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        runWithMultipleFiles(fileExtensions) {
            runInEdtAndWait {
                myFixture.file.findElementAt(myFixture.caretOffset)?.let {
                    val symbol = DotEnvKeySymbol(it.text, it.containingFile, it.textRange)
                    myFixture.renameTarget(symbol, newName)
                }
            }
        }
    }

    private fun runWithMultipleFiles(fileExtensions: Array<out String>, testOp: () -> Unit) {
        fileExtensions
            .map { filenamePrefixForCurrentTest(it) }
            .toTypedArray()
            .let { name -> myFixture.configureByFiles(*name).map { it.virtualFile } }
            .also { testOp.invoke() }
            .forEach {
                myFixture.openFileInEditor(it)
                assertSameLinesWithFile(
                    "${testDataPath}/${it.name}.txt",
                    myFixture.editor.document.text
                )
            }
    }

    override fun getBasePath(): String = "testResources/ru/adelf/idea/dotenv/tests"

    protected override fun getTestDataPath(): String = "$basePath/dotenv/fixtures"

}