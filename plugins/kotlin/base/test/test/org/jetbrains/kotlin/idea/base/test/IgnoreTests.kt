// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.test.utils.withExtension
import org.junit.Assert
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.useLines
import kotlin.io.path.writeLines

object IgnoreTests {
    private const val INSERT_DIRECTIVE_AUTOMATICALLY = false // TODO use environment variable instead
    private const val ALWAYS_CONSIDER_TEST_AS_PASSING = false // TODO use environment variable instead

    fun runTestIfEnabledByFileDirective(
        testFile: Path,
        enableTestDirective: String,
        vararg additionalFilesExtensions: String,
        directivePosition: DirectivePosition = DirectivePosition.FIRST_LINE_IN_FILE,
        test: (isTestEnabled: Boolean) -> Unit,
    ) {
        runTestIfEnabledByDirective(
            testFile,
            EnableOrDisableTestDirective.Enable(enableTestDirective),
            directivePosition,
            computeAdditionalFilesByExtensions(testFile, additionalFilesExtensions.asList()),
            test
        )
    }

    fun runTestWithFixMeSupport(
        testFile: Path,
        directivePosition: DirectivePosition = DirectivePosition.FIRST_LINE_IN_FILE,
        test: (isTestEnabled: Boolean) -> Unit
    ) {
        runTestIfEnabledByDirective(
            testFile,
            EnableOrDisableTestDirective.Disable(DIRECTIVES.FIX_ME),
            directivePosition,
            additionalFiles = emptyList(),
            test = test
        )
    }

    fun runTestIfNotDisabledByFileDirective(
        testFile: Path,
        disableTestDirective: String,
        vararg additionalFilesExtensions: String,
        directivePosition: DirectivePosition = DirectivePosition.FIRST_LINE_IN_FILE,
        test: (isTestEnabled: Boolean) -> Unit
    ) {
        runTestIfNotDisabledByFileDirective(
            testFile,
            disableTestDirective,
            { mainTestFile -> computeAdditionalFilesByExtensions(mainTestFile, additionalFilesExtensions.asList()) },
            directivePosition,
            test
        )
    }

    fun runTestIfNotDisabledByFileDirective(
        testFile: Path,
        disableTestDirective: String,
        computeAdditionalFiles: (mainTestFile: Path) -> List<Path>,
        directivePosition: DirectivePosition = DirectivePosition.FIRST_LINE_IN_FILE,
        test: (isTestEnabled: Boolean) -> Unit
    ) {
        runTestIfEnabledByDirective(
            testFile,
            EnableOrDisableTestDirective.Disable(disableTestDirective),
            directivePosition,
            computeAdditionalFiles(testFile),
            test
        )
    }

    /**
     * Runs [test] normally if it is enabled via [isEnabled]. If not, runs the disabled test experimentally and reports an error if it
     * passes unexpectedly.
     *
     * [runTestIfEnabled] should be used instead of a directive function if an enable/disable directive is not suitable for the test data.
     *
     * @param testFile A path to the file which contains the "enable" or "disable" setting.
     */
    fun runTestIfEnabled(
        isEnabled: Boolean,
        testFile: Path,
        test: () -> Unit
    ) {
        if (isEnabled) {
            test()
            return
        }

        try {
            test()
        } catch (_: Throwable) {
            return
        }

        throw AssertionError("The test passes but is disabled. Please enable the test in the following file: `$testFile`.")
    }

    private fun runTestIfEnabledByDirective(
        testFile: Path,
        directive: EnableOrDisableTestDirective,
        directivePosition: DirectivePosition,
        additionalFiles: List<Path>,
        test: (isTestEnabled: Boolean) -> Unit
    ) {
        check(!
              (directive is EnableOrDisableTestDirective.Enable && (
                     directive.directiveText == DIRECTIVES.FIR_IDENTICAL || directive.directiveText == DIRECTIVES.FIR_COMPARISON))) {
            "It's not allowed to run runTestIfEnabledByDirective with FIR_IDENTICAL or FIR_COMPARISON"
        }
        if (ALWAYS_CONSIDER_TEST_AS_PASSING) {
            test(true)
            return
        }
        val testIsEnabled = directive.isEnabledInFile(testFile)

        try {
            test(testIsEnabled)
        } catch (e: Throwable) {
            if (testIsEnabled) {
                if (directive is EnableOrDisableTestDirective.Disable) {
                    try {
                        handleTestWithWrongDirective(testPasses = false, testFile, directive, directivePosition, additionalFiles)
                    } catch (e: AssertionError) {
                        LoggerFactory.getLogger("test").info(e.message)
                    }
                }
                throw e
            }
            return
        }

        if (!testIsEnabled) {
            handleTestWithWrongDirective(testPasses = true, testFile, directive, directivePosition, additionalFiles)
        }
    }


    private fun handleTestWithWrongDirective(
        testPasses: Boolean,
        testFile: Path,
        directive: EnableOrDisableTestDirective,
        directivePosition: DirectivePosition,
        additionalFiles: List<Path>,
    ) {
        val verb = when (testPasses) {
            false -> "do not pass"
            true -> "passes"
        }

        if (INSERT_DIRECTIVE_AUTOMATICALLY) {
            when (directive) {
                is EnableOrDisableTestDirective.Disable -> {
                    testFile.removeDirectivesFromFileAndAdditionalFiles(directive, additionalFiles)
                }
                is EnableOrDisableTestDirective.Enable -> {
                    testFile.insertDirectivesToFileAndAdditionalFile(directive, additionalFiles, directivePosition)
                }
            }

            val modifiedFiles = buildList {
                add(testFile.fileName.toString())
                addAll(additionalFiles)
            }
            throw AssertionError(
                "Looks like the test was ${directive.fixDirectiveMessage}(e)d to ${modifiedFiles.joinToString()}"
            )
        }

        throw AssertionError(
            "Looks like the test $verb, please ${directive.fixDirectiveMessage} ${testFile.fileName} (see $testFile)"
        )
    }

    private fun computeAdditionalFilesByExtensions(mainTestFile: Path, additionalFilesExtensions: List<String>): List<Path> {
        return additionalFilesExtensions.mapNotNull { mainTestFile.getSiblingFile(it) }
    }

    private fun Path.insertDirectivesToFileAndAdditionalFile(
        directive: EnableOrDisableTestDirective,
        additionalFiles: List<Path>,
        directivePosition: DirectivePosition,
    ) {
        insertDirective(directive, directivePosition)
        additionalFiles.forEach { it.insertDirective(directive, directivePosition) }
    }

    private fun Path.removeDirectivesFromFileAndAdditionalFiles(
        directive: EnableOrDisableTestDirective,
        additionalFiles: List<Path>,
    ) {
        removeDirective(directive)
        additionalFiles.forEach { it.removeDirective(directive) }
    }

    private fun Path.getSiblingFile(extension: String): Path? {
        val siblingName = fileName.toString() + "." + extension.removePrefix(".")
        return resolveSibling(siblingName).takeIf(Files::exists)
    }

    private sealed class EnableOrDisableTestDirective {
        abstract val directiveText: String
        abstract val fixDirectiveMessage: String

        abstract fun isEnabledIfDirectivePresent(isDirectivePresent: Boolean): Boolean

        data class Enable(override val directiveText: String) : EnableOrDisableTestDirective() {
            override val fixDirectiveMessage: String get() = "add $directiveText to"

            override fun isEnabledIfDirectivePresent(isDirectivePresent: Boolean): Boolean = isDirectivePresent
        }

        data class Disable(override val directiveText: String) : EnableOrDisableTestDirective() {
            override val fixDirectiveMessage: String get() = "remove $directiveText from"
            override fun isEnabledIfDirectivePresent(isDirectivePresent: Boolean): Boolean = !isDirectivePresent
        }
    }

    private fun EnableOrDisableTestDirective.isEnabledInFile(file: Path): Boolean {
        val isDirectivePresent = containsDirective(file, this)
        return isEnabledIfDirectivePresent(isDirectivePresent)
    }

    private fun containsDirective(file: Path, directive: EnableOrDisableTestDirective): Boolean {
        if (file.notExists()) return false
        if (file.useLines { lines -> lines.any { it.isLineWithDirective(directive) } }) return true
        return InTextDirectivesUtils.textWithDirectives(file.parent.toFile()).lineSequence().any { it.isLineWithDirective(directive) }
    }

    private fun String.isLineWithDirective(directive: EnableOrDisableTestDirective): Boolean =
        substringBefore(':').trim() == directive.directiveText.trim()

    private fun Path.insertDirective(directive: EnableOrDisableTestDirective, directivePosition: DirectivePosition) {
        if (notExists()) {
            createFile()
        }
        toFile().apply {
            val originalText = readText()
            val textWithDirective = when (directivePosition) {
                DirectivePosition.FIRST_LINE_IN_FILE -> "${directive.directiveText}\n$originalText"
                DirectivePosition.LAST_LINE_IN_FILE -> "$originalText\n${directive.directiveText}"
            }
            writeText(textWithDirective)
        }
    }


    private fun Path.removeDirective(directive: EnableOrDisableTestDirective) {
        toFile().apply {
            val lines = useLines { it.toList() }
            writeLines(lines.filterNot { it.isLineWithDirective(directive) })
        }
    }

    object DIRECTIVES {
        @Deprecated(message = "use IGNORE_K2 instead")
        const val FIR_COMPARISON: String = "// FIR_COMPARISON"
        @Deprecated(message = "use IGNORE_K2 instead")
        const val FIR_COMPARISON_MULTILINE_COMMENT: String = "/* FIR_COMPARISON */"

        const val IGNORE_K2: String = "// IGNORE_K2"
        const val IGNORE_K2_MULTILINE_COMMENT: String = "/* IGNORE_K2 */"

        @Deprecated(message = "use IGNORE_K2 instead")
        const val IGNORE_FIR: String = "// IGNORE_FIR"
        @Deprecated(message = "use IGNORE_K2_MULTILINE_COMMENT instead")
        const val IGNORE_FIR_MULTILINE_COMMENT: String = "/* IGNORE_FIR */"

        const val FIX_ME: String = "// FIX_ME: "

        const val FIR_IDENTICAL: String = "// FIR_IDENTICAL"

        const val IGNORE_FE10_BINDING_BY_FIR: String = "// IGNORE_FE10_BINDING_BY_FIR"

        const val IGNORE_K1: String = "// IGNORE_K1"

        @JvmStatic
        fun of(mode: KotlinPluginMode): String = if (mode == KotlinPluginMode.K2) IGNORE_K2 else IGNORE_K1
    }

    enum class FileExtension {
        K2,

        @Deprecated(message = "use K2 instead")
        FIR;

        override fun toString(): String = name.lowercase()
    }

    enum class DirectivePosition {
        FIRST_LINE_IN_FILE, LAST_LINE_IN_FILE
    }

    private val isTeamCityBuild: Boolean
        get() = System.getenv("TEAMCITY_VERSION") != null

    fun getK2TestFile(
        originalTestFile: File,
        k2Extension: FileExtension = FileExtension.K2,
        vararg additionalFilesExtensions: String
    ): File {
        if (originalTestFile.readLines().any { it.startsWith(DIRECTIVES.FIR_IDENTICAL) }) {
            return originalTestFile
        }
        val k2TestFile = deriveK2TestFile(originalTestFile, k2Extension)
        if (!k2TestFile.exists()) {
            FileUtil.copy(originalTestFile, k2TestFile)
        }
        for (extension in additionalFilesExtensions) {
            val additionalK2File = k2TestFile.withExtension(k2TestFile.extension + extension)
            val additionalOriginalFile = originalTestFile.withExtension(originalTestFile.extension + extension)
            if (!additionalK2File.exists() && additionalOriginalFile.exists()) {
                FileUtil.copy(additionalOriginalFile, additionalK2File)
            }
        }
        return k2TestFile
    }

    fun getK2TestFileIfK2Passing(
        originalTestFile: File,
        passingDirective: String,
        k2Extension: FileExtension = FileExtension.K2,
        vararg additionalFilesExtensions: String
    ): File {
        if (!InTextDirectivesUtils.isDirectiveDefined(originalTestFile.readText(), passingDirective)) {
            return originalTestFile
        }
        return getK2TestFile(originalTestFile, k2Extension, *additionalFilesExtensions)
    }


    fun cleanUpIdenticalK2TestFile(
        originalTestFile: File,
        k2Extension: FileExtension = FileExtension.K2,
        k2TestFile: File = deriveK2TestFile(originalTestFile, k2Extension),
        additionalFileToMarkFirIdentical: File? = null,
        additionalFileToDeleteIfIdentical: File? = null,
        additionalFilesToCompare: Collection<Pair<File, File>> = emptyList()
    ) {
        if (k2TestFile.exists() &&
            k2TestFile.readText().trim() == originalTestFile.readText().trim() &&
            additionalFilesToCompare.all { (a, b) ->
                if (!a.exists() || !b.exists()) false
                else a.readText().trim() == b.readText().trim()
            }
        ) {
            val message = if (isTeamCityBuild) {
                "Please remove $k2TestFile and add // FIR_IDENTICAL to test source file $originalTestFile"
            } else {
                // The FIR test file is identical with the original file. We should remove the FIR file and mark "FIR_IDENTICAL" in the
                // original file
                k2TestFile.delete()
                originalTestFile.prependFirIdentical()
                additionalFileToMarkFirIdentical?.prependFirIdentical()
                if (additionalFileToDeleteIfIdentical?.exists() == true) additionalFileToDeleteIfIdentical.delete()

                "Deleted $k2TestFile, added // FIR_IDENTICAL to test source file $originalTestFile"
            }
            Assert.fail(
                """
                        Dumps via FIR & via old FE are the same. 
                        $message
                        Please re-run the test now
                    """.trimIndent()
            )
        }
    }

    private fun File.prependFirIdentical() {
        val content = readText()
        if (content.contains(DIRECTIVES.FIR_IDENTICAL)) return
        writer().use {
            it.appendLine(DIRECTIVES.FIR_IDENTICAL)
            it.append(content)
        }
    }

    private fun deriveK2TestFile(originalTestFile: File, k2Extension: FileExtension): File {
        val name = originalTestFile.name
        return originalTestFile.parentFile.resolve(deriveK2FileName(name, k2Extension))
    }

    fun deriveK2FileName(fileName: String, k2Extension: FileExtension): String =
        fileName.substringBeforeLast('.') + ".$k2Extension." + fileName.substringAfterLast('.')
}
