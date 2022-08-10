package org.jetbrains.completion.full.line.markers

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.jetbrains.python.PythonLanguage
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter
import org.apache.commons.io.filefilter.RegexFileFilter
import org.jetbrains.completion.full.line.Marker
import org.jetbrains.completion.full.line.local.LocalFullLineCompletionTestCase
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

class PythonMarkerTestCase : MarkerTestCase(PythonLanguage.INSTANCE) {
    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonMarkers")
    fun `test python markers with local model and plugin`(marker: Marker) = wrapJUnit3TestCase {
        initModel()
        doTestMarkers(marker)
    }

    companion object {
        @JvmStatic
        fun pythonMarkers() = markers("python")
    }
}

class JavaMarkerTestCase : MarkerTestCase(JavaLanguage.INSTANCE) {
    @ParameterizedTest(name = "{0}")
    @MethodSource("javaMarkers")
    fun `test java markers with local model and plugin`(marker: Marker) = wrapJUnit3TestCase {
        initModel()
        doTestMarkers(marker)
    }

    companion object {
        @JvmStatic
        fun javaMarkers() = markers("java")
    }
}

class KotlinMarkerTestCase : MarkerTestCase(KotlinLanguage.INSTANCE) {
    @ParameterizedTest(name = "{0}")
    @MethodSource("kotlinMarkers")
    fun `test kotlin markers with local model and plugin`(marker: Marker) = wrapJUnit3TestCase {
        initModel()
        doTestMarkers(marker)
    }

    companion object {
        @JvmStatic
        fun kotlinMarkers() = markers("kotlin")
    }
}

abstract class MarkerTestCase(private val language: Language) : LocalFullLineCompletionTestCase(language) {
    fun test_init_model() {
        initModel()
    }

    override fun getTestDataPath() = MARKERS_PATH

    protected fun doTestMarkers(marker: Marker) {
        val context = marker.code.take(marker.offset) + "<caret>" + marker.code.drop(marker.offset)
        val relFilePath = if (language.id == JavaLanguage.INSTANCE.id) "../${marker.filename}" else marker.filename

        myFixture.addFileToProject(relFilePath, context)
        myFixture.configureByFile(relFilePath)
        myFixture.completeBasic()

        Assertions.assertNotNull(this.myFixture.lookupElements, "Lookup is not shown")

        val variants = this.myFixture.lookupElements!!.filterIsInstance<FullLineLookupElement>().map { it.lookupString }

        Assertions.assertTrue(variants.isNotEmpty(), "Variants are empty")
        Assertions.assertTrue(
            variants.any { variant -> marker.result.matches(variant) },
            "One of suggestions: `${variants.joinToString("\n\t=>", prefix = "\n\t=>")}` Must match the result: `${marker.result}`."
        )
    }

    @Suppress("unused")
    companion object {
        private const val MARKERS_PATH = "testData/markers"

        fun markers(language: String): List<Arguments> {
            val rootFolder = getResource("$MARKERS_PATH/$language")
            if (rootFolder?.exists() != true) return emptyList()

            return FileUtils.listFiles(rootFolder, RegexFileFilter("^(.*?)"), DirectoryFileFilter.DIRECTORY)
                .mapIndexed { i, file -> Arguments.of(Marker.fromMap(language, file, rootFolder, false)) }
        }

        private fun getResource(name: String): File? {
            return MarkerTestCase::class.java.classLoader.getResource(name)?.path?.let { File(it) }
        }
    }
}
